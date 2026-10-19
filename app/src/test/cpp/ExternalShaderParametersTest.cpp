#include "renderer/ExternalShaderParameters.h"
#include <cassert>
#include <limits>

int main() {
    using namespace MelonDSAndroid;
    assert(getExternalShaderParameters() == (std::array<float, 6>{}));
    const std::array<float, 6> valid{16, 4, 1.4166667f, -14, 0, 1};
    setExternalShaderParameters(valid);
    assert(getExternalShaderParameters() == valid);
    setExternalShaderParameters({16, 4, std::numeric_limits<float>::quiet_NaN(), 14, 1, 1});
    assert(getExternalShaderParameters() == valid);
    setExternalShaderParameters({100001, 0, 0, 0, 0, 0});
    assert(getExternalShaderParameters() == valid);
    setExternalShaderParameters({0, 0, 0, 0, 0, 0});
    assert(getExternalShaderParameters() == (std::array<float, 6>{}));
}
