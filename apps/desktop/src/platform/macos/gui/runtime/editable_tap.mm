#include "editable_field.h"
#include <algorithm>
#include <chrono>
#include <cmath>

#include "runtime/gui_runtime.h"
#include "runtime/peer_event_handler.h"
#include "stream_names.h"

namespace crossdesk {
namespace {
using Clock = std::chrono::steady_clock;
using namespace std::chrono_literals;

bool SameScreen(const DisplayInfo& a, const DisplayInfo& b) {
  return a.handle && a.handle == b.handle && a.left == b.left &&
         a.right == b.right && a.top == b.top && a.bottom == b.bottom;
}

}  // namespace

void GuiRuntime::RecordAppliedTap(const std::string& peer, const RemoteAction& action,
                                  const AppliedMousePoint* point, InputOutcome outcome) {
  const auto now = Clock::now();
  std::lock_guard lock(tap_mutex_);
  auto previous = std::move(pending_tap_);
  pending_tap_.reset();
  ++tap_revision_;
  if (outcome != InputOutcome::applied || !point || !point->screen.handle ||
      point->screen.width <= 0 || point->screen.height <= 0 || action.ime_source.expired() ||
      action.ime_epoch != tap_epoch_ || now - action.ime_received > 900ms ||
      !(action.m.x >= 0 && action.m.x <= 1 && action.m.y >= 0 && action.m.y <= 1))
    return;
  const auto& screen = point->screen;
  const double x = point->x, y = point->y;
  if (action.m.flag == MouseFlag::left_down) {
    pending_tap_ = EditableTap{peer, action.ime_source, tap_revision_, action.ime_epoch,
        action.ime_tap, action.ime_display, screen, x, y, action.ime_received};
  } else if (action.m.flag == MouseFlag::left_up && previous &&
             previous->released == Clock::time_point{} && previous->peer == peer &&
             previous->source.lock() == action.ime_source.lock() &&
             previous->epoch == action.ime_epoch && previous->id == action.ime_tap &&
             previous->display == action.ime_display && SameScreen(previous->screen, screen) &&
             previous->x == x && previous->y == y && now - previous->received < 900ms) {
    previous->released = now;
    previous->revision = tap_revision_;
    pending_tap_ = std::move(previous);
  }
}

void GuiRuntime::TickEditableTap() {
  auto valid = [&](const EditableTap& tap) {
    const auto source = ControllerEvents(tap.peer);
    if (!source || source != tap.source.lock() || !source->DataAllowed() ||
        !IsAuthorizedController(tap.peer) || !TrustedControllerAllowed(tap.peer) ||
        tap.display != selected_display_ || tap.epoch != tap_epoch_ ||
        Clock::now() - tap.received > 1000ms) return false;
    const auto screens = devices_.display_info_list();
    if (tap.display < 0 || size_t(tap.display) >= screens.size() ||
        !SameScreen(tap.screen, screens[tap.display])) return false;
    std::lock_guard lock(tap_mutex_);
    return tap.revision == tap_revision_;
  };
  if (editable_probe_.valid()) {
    if (editable_probe_.wait_for(0ms) != std::future_status::ready) return;
    const bool editable = editable_probe_.get();
    const auto tap = std::move(*probing_tap_);
    probing_tap_.reset();
    auto source = tap.source.lock();
    if (source && valid(tap)) {
      auto reply = nlohmann::json{{"type","yudesk_ime"},{"id",tap.id},
                                  {"display",tap.display},{"editable",editable}}.dump();
      WithController(tap.peer, [&](MiniRtcPeerPtr* peer, const std::string& wire) {
        std::lock_guard lock(tap_mutex_);
        if (tap.revision != tap_revision_ || tap.epoch != tap_epoch_ ||
            tap.display != selected_display_ || Clock::now() - tap.received > 1000ms) return -1;
        return PeerSendReliableDataFrameToPeer(peer, reply.data(), reply.size(),
                                               kControlStream, wire.data(), wire.size());
      }, true, static_cast<PeerEventHandler*>(source.get()));
    }
  }
  {
    std::lock_guard lock(tap_mutex_);
    if (!pending_tap_ || pending_tap_->released == Clock::time_point{} ||
        Clock::now() - pending_tap_->released < 55ms) return;
    probing_tap_ = std::move(pending_tap_);
    pending_tap_.reset();
  }
  const auto& tap = *probing_tap_;
  if (valid(tap)) {
    editable_probe_ = std::async(std::launch::async, EditableAt, tap.x, tap.y,
                                 tap.received + 1000ms);
  } else {
    probing_tap_.reset();
  }
}
}  // namespace crossdesk
