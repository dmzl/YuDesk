#import <AppKit/AppKit.h>
#import <ApplicationServices/ApplicationServices.h>

#include "editable_field.h"

#include <optional>
#include <thread>

namespace crossdesk {
namespace {
// Copy only non-content attributes; every AX request has a short timeout.
CFTypeRef Attribute(AXUIElementRef element, CFStringRef key) {
  if (!element) return nullptr;
  AXUIElementSetMessagingTimeout(element, 0.08f);
  CFTypeRef value = nullptr;
  AXUIElementCopyAttributeValue(element, key, &value);
  return value;
}
AXUIElementRef Element(AXUIElementRef source, CFStringRef key) {
  auto value = Attribute(source, key);
  if (!value || CFGetTypeID(value) == AXUIElementGetTypeID())
    return static_cast<AXUIElementRef>(value);
  CFRelease(value);
  return nullptr;
}
// A newly activated web view may expose neither focus nor hit on its first AX request.
std::optional<bool> ProbeEditableAt(double x, double y, std::chrono::steady_clock::time_point deadline) {
  @autoreleasepool {
    if (std::chrono::steady_clock::now() >= deadline || !AXIsProcessTrusted()) return false;
    auto system = AXUIElementCreateSystemWide();
    AXUIElementSetMessagingTimeout(system, 0.08f);
    AXUIElementRef hit = nullptr;
    AXUIElementCopyElementAtPosition(system, x, y, &hit);
    auto focus = Element(system, kAXFocusedUIElementAttribute);
    // A system-wide element changes the process default. Restore it immediately.
    AXUIElementSetMessagingTimeout(system, 0);
    CFRelease(system);
    if (!hit || !focus) {
      if (hit) CFRelease(hit);
      if (focus) CFRelease(focus);
      return std::nullopt;
    }
    pid_t pid = 0;
    bool matched = false;
    if (focus && AXUIElementGetPid(focus, &pid) == kAXErrorSuccess &&
        pid == NSWorkspace.sharedWorkspace.frontmostApplication.processIdentifier) {
      for (int depth = 0; hit && depth < 5 && std::chrono::steady_clock::now() < deadline; ++depth) {
        pid_t hit_pid = 0;
        if (AXUIElementGetPid(hit, &hit_pid) != kAXErrorSuccess || hit_pid != pid) break;
        if (CFEqual(hit, focus)) { matched = true; break; }
        auto parent = Element(hit, kAXParentAttribute);
        CFRelease(hit);
        hit = parent;
      }
    }
    if (hit) CFRelease(hit);
    bool editable = false;
    if (matched && std::chrono::steady_clock::now() < deadline) {
      auto role = Attribute(focus, kAXRoleAttribute);
      CFTypeRef enabled = nullptr;
      auto enabled_status = AXUIElementCopyAttributeValue(focus, kAXEnabledAttribute, &enabled);
      CFTypeRef explicit_editable = nullptr;
      auto editable_status = AXUIElementCopyAttributeValue(focus, CFSTR("AXEditable"), &explicit_editable);
      const bool text = role && (CFEqual(role, kAXTextFieldRole) ||
          CFEqual(role, kAXTextAreaRole) || CFEqual(role, kAXComboBoxRole));
      Boolean writable = false;
      if (text && !explicit_editable)
        AXUIElementIsAttributeSettable(focus, kAXValueAttribute, &writable);
      // AppKit NSTextView can omit AXEnabled. Its AXValue is settable only
      // while editable. An explicit disable or a failed AX request still wins.
      const bool writable_area = role && CFEqual(role, kAXTextAreaRole) &&
          enabled_status == kAXErrorAttributeUnsupported &&
          editable_status == kAXErrorAttributeUnsupported && writable;
      editable = writable_area || (enabled == kCFBooleanTrue &&
          (explicit_editable == kCFBooleanTrue || (text && writable && !explicit_editable)));
      if (role) CFRelease(role);
      if (enabled) CFRelease(enabled);
      if (explicit_editable) CFRelease(explicit_editable);
    }
    if (focus) CFRelease(focus);
    return editable && std::chrono::steady_clock::now() < deadline;
  }
}
}  // namespace

bool EditableAt(double x, double y, std::chrono::steady_clock::time_point deadline) {
  using namespace std::chrono_literals;
  for (int attempt = 0; attempt < 3; ++attempt) {
    auto result = ProbeEditableAt(x, y, deadline);
    if (result.has_value()) return *result;
    if (attempt == 2 || std::chrono::steady_clock::now() + 65ms >= deadline) break;
    std::this_thread::sleep_for(65ms);
  }
  return false;
}
}  // namespace crossdesk
