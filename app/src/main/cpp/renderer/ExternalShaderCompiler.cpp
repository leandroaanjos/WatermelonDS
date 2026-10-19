#include "ExternalShaderCompiler.h"
#include "ExternalShaderTemplate.h"
#include <glslang/Public/ShaderLang.h>
#include <glslang/Public/ResourceLimits.h>
#include <SPIRV/GlslangToSpv.h>
#include <mutex>

namespace MelonDSAndroid {
namespace {
std::mutex compileMutex;
std::mutex stateMutex;
ExternalShaderSnapshot installed;
std::string pipelineError;
}

std::string compileExternalShader(const std::string& body) {
    // Allow the bounded source plus generated parameter bindings.
    if (body.empty() || body.size() > 135168) return "Shader source exceeds the supported size.";
    std::lock_guard<std::mutex> compilationLock(compileMutex);
    static const bool initialized = glslang::InitializeProcess();
    if (!initialized) return "Could not initialize the GLSL compiler.";
    std::string source(wmPresenterSource);
    const std::string begin = "// WM_EXTERNAL_BEGIN";
    const std::string end = "// WM_EXTERNAL_END";
    const auto start = source.find(begin);
    const auto finish = source.find(end, start);
    if (start == std::string::npos || finish == std::string::npos) return "Missing external shader template.";
    source.replace(start, finish + end.size() - start, "\n#line 1 1\n" + body + "\n#line 1 0\n");
    const char* text = source.c_str();
    glslang::TShader shader(EShLangFragment);
    shader.setStrings(&text, 1);
    shader.setEnvInput(glslang::EShSourceGlsl, EShLangFragment, glslang::EShClientVulkan, 450);
    shader.setEnvClient(glslang::EShClientVulkan, glslang::EShTargetVulkan_1_0);
    shader.setEnvTarget(glslang::EShTargetSpv, glslang::EShTargetSpv_1_0);
    const auto messages = static_cast<EShMessages>(EShMsgSpvRules | EShMsgVulkanRules);
    if (!shader.parse(GetDefaultResources(), 450, false, messages)) return shader.getInfoLog();
    glslang::TProgram program;
    program.addShader(&shader);
    if (!program.link(messages)) return program.getInfoLog();
    std::vector<uint32_t> words;
    glslang::SpvOptions options;
    options.disableOptimizer = true;
    glslang::GlslangToSpv(*program.getIntermediate(EShLangFragment), words, &options);
    if (words.empty()) return "The compiler produced no SPIR-V.";
    std::lock_guard<std::mutex> stateLock(stateMutex);
    installed.words = std::move(words);
    ++installed.generation;
    pipelineError.clear();
    return {};
}
void clearExternalShader() {
    std::lock_guard<std::mutex> compilationLock(compileMutex);
    std::lock_guard<std::mutex> lock(stateMutex);
    installed.words.clear();
    ++installed.generation;
    pipelineError.clear();
}
uint64_t getExternalShaderGeneration() {
    std::lock_guard<std::mutex> lock(stateMutex);
    return installed.words.empty() ? 0 : installed.generation;
}
ExternalShaderSnapshot getExternalShaderSnapshot() {
    std::lock_guard<std::mutex> lock(stateMutex);
    return installed;
}
void setExternalShaderPipelineError(const std::string& error) {
    std::lock_guard<std::mutex> lock(stateMutex);
    pipelineError = error;
}
std::string getExternalShaderError() {
    std::lock_guard<std::mutex> lock(stateMutex);
    return pipelineError;
}
}
