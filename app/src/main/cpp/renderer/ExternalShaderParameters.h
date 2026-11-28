#pragma once

#include <array>
#include <mutex>
#include <cmath>

namespace MelonDSAndroid {
inline std::mutex externalShaderParametersMutex;
inline std::array<float, 6> externalShaderParameters{};

inline std::array<float, 6> getExternalShaderParameters() {
    std::lock_guard<std::mutex> lock(externalShaderParametersMutex);
    return externalShaderParameters;
}

inline void setExternalShaderParameters(const std::array<float, 6>& values) {
    for (float value : values) {
        if (!std::isfinite(value) || std::abs(value) > 100000.0f) return;
    }
    std::lock_guard<std::mutex> lock(externalShaderParametersMutex);
    externalShaderParameters = values;
}
}
