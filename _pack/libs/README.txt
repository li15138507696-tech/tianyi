编译期本体依赖（不要手改）

来源：D:\cxdownload\guzhenrn蛊真人模组本体10.20.jar
      → META-INF/jarjar/guzhenren-0.1-neoforge-1.21.1.jar（jarjar 嵌套）
SHA256：b6d314d3810b8bc5fcfdbc4f588db8721f0efdfc14d27a0a06d88143a01fe2bc
说明：外层 10.20 包的 modId 是 guzhenren_library（壳），
      真正含 net.guzhenren.* 代码的是上面这份嵌套 jar。
      与原先 _reverse/nested 下那份逐字节相同，切换只是为了让"编译依据"可追溯。
