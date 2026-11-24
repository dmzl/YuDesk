#import <AppKit/AppKit.h>
#import <ApplicationServices/ApplicationServices.h>

#include "editable_field.h"

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
}  // namespace

bool EditableAt(double x, double y, std::chrono::steady_clock::time_point deadline) {
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
      auto enabled = Attribute(focus, kAXEnabledAttribute);
      auto explicit_editable = Attribute(focus, CFSTR("AXEditable"));
      const bool text = role && (CFEqual(role, kAXTextFieldRole) ||
          CFEqual(role, kAXTextAreaRole) || CFEqual(role, kAXComboBoxRole));
      Boolean writable = false;
      if (text && !explicit_editable)
        AXUIElementIsAttributeSettable(focus, kAXValueAttribute, &writable);
      editable = enabled == kCFBooleanTrue && (explicit_editable == kCFBooleanTrue ||
                 (text && writable && !explicit_editable));
      if (role) CFRelease(role);
      if (enabled) CFRelease(enabled);
      if (explicit_editable) CFRelease(explicit_editable);
    }
    if (focus) CFRelease(focus);
    return editable && std::chrono::steady_clock::now() < deadline;
  }
}
}  // namespace crossdesk
