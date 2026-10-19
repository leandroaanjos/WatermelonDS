#include "renderer/ExternalShaderCompiler.h"
#include <cassert>
#include <fstream>
#include <iostream>
#include <iterator>
#include <regex>

int main() {
    using namespace MelonDSAndroid;
    for (const char* path : {"shaders/lcd1x_fine.wmshader", "shaders/lcd1x_nds.wmshader", "shaders/zfast_lcd.wmshader", "shaders/lcd3x.wmshader"}) {
        std::ifstream input(path);
        assert(input.good());
        const std::string source((std::istreambuf_iterator<char>(input)), std::istreambuf_iterator<char>());
        std::string definitions;
        const std::regex parameter("// @parameter ([A-Z][A-Z0-9_]*)");
        unsigned index = 0;
        for (auto it = std::sregex_iterator(source.begin(), source.end(), parameter); it != std::sregex_iterator(); ++it) {
            assert(index < 6);
            definitions += "#define " + (*it)[1].str() + " (pushConstants.externalParameters[" + std::to_string(index++) + "])\n";
        }
        const auto error = compileExternalShader(definitions + source);
        if (!error.empty()) { std::cerr << path << ": " << error << std::endl; return 1; }
        assert(!getExternalShaderSnapshot().words.empty());
    }
    const auto installed = getExternalShaderSnapshot();
    assert(!compileExternalShader("this is invalid GLSL").empty());
    assert(getExternalShaderSnapshot().words == installed.words);
    assert(getExternalShaderGeneration() == installed.generation);
    clearExternalShader();
    assert(getExternalShaderGeneration() == 0);
    std::cout << "All shader examples compile; invalid imports preserve the installed shader.\n";
}
