#pragma once
#include <cstdint>
#include <string>
#include <vector>

namespace MelonDSAndroid {
struct ExternalShaderSnapshot {
    std::vector<uint32_t> words;
    uint64_t generation = 0;
};
// Compilation failure leaves the last working source installed.
std::string compileExternalShader(const std::string& body);
void clearExternalShader();
uint64_t getExternalShaderGeneration();
ExternalShaderSnapshot getExternalShaderSnapshot();
void setExternalShaderPipelineError(const std::string& error);
std::string getExternalShaderError();
}
