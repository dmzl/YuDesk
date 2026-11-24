#pragma once
#include <chrono>

namespace crossdesk {
bool EditableAt(double x, double y, std::chrono::steady_clock::time_point deadline);
}
