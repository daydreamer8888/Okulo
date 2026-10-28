"""Rebuild the ARM64 CPU runtime and JNI bridge for the packaged FP32 models."""
import argparse
import os
import shutil
import subprocess
import sys
import urllib.request
import zipfile
from pathlib import Path

VERSION = "1.30.0"
OPERATORS = (
    "ai.onnx;17;Add,And,AveragePool,Cast,Clip,Concat,Constant,ConstantOfShape,Conv,Div,"
    "Einsum,Equal,Erf,Expand,Flatten,Floor,Gather,GatherElements,GreaterOrEqual,Identity,"
    "LayerNormalization,Less,LessOrEqual,MatMul,MaxPool,Mod,Mul,Not,Pow,Range,ReduceMax,"
    "ReduceMean,ReduceSum,Relu,Reshape,Resize,ScatterND,Shape,Sigmoid,Slice,Softmax,"
    "Split,Sqrt,Squeeze,Sub,Tile,TopK,Transpose,Unsqueeze,Where\n"
    "com.microsoft;1;BiasGelu,FusedConv,FusedMatMul,QuickGelu\n"
)
BASE_URL = (
    "https://repo.maven.apache.org/maven2/com/microsoft/onnxruntime/"
    f"onnxruntime-android/{VERSION}/onnxruntime-android-{VERSION}.aar"
)


def compile_jni(source, build, toolchain, java_home):
    generated = build / "jni"
    classes, headers = generated / "classes", generated / "headers"
    classes.mkdir(parents=True, exist_ok=True)
    headers.mkdir(parents=True, exist_ok=True)
    suffix = ".exe" if os.name == "nt" else ""
    java_sources = sorted((source / "java/src/main/java").rglob("*.java"))
    java_sources += sorted((source / "java/src/main/android").rglob("*.java"))
    subprocess.run([
        str(java_home / "bin" / f"javac{suffix}"), "-encoding", "UTF-8",
        "-h", str(headers), "-d", str(classes), *map(str, java_sources),
    ], check=True)
    native = source / "java/src/main/native"
    command = [
        str(toolchain / "bin" / f"clang{suffix}"), "--target=aarch64-linux-android26",
        "-shared", "-fPIC", "-O3", "-DNDEBUG", "-ffunction-sections", "-fdata-sections",
        f"-ffile-prefix-map={source.as_posix()}=onnxruntime",
        f"-ffile-prefix-map={build.as_posix()}=build",
        "-Wl,--gc-sections", "-Wl,--no-undefined", "-Wl,-z,max-page-size=16384",
        "-Wl,-soname,libonnxruntime4j_jni.so", "-o", str(build / "libonnxruntime4j_jni.so"),
    ]
    for include in [
        source / "include", source / "include/onnxruntime/core/session",
        source / "orttraining/orttraining/training_api/include", native, build, headers,
    ]:
        command += ["-I", str(include)]
    command += [*map(str, sorted(native.glob("*.c"))), "-L", str(build), "-lonnxruntime", "-ldl"]
    subprocess.run(command, check=True)


def package(base_aar, build, output):
    replacements = {
        f"jni/arm64-v8a/{name}": (build / name).read_bytes()
        for name in ("libonnxruntime.so", "libonnxruntime4j_jni.so")
    }
    output.parent.mkdir(parents=True, exist_ok=True)
    partial = output.with_suffix(".partial")
    try:
        with zipfile.ZipFile(base_aar) as original, zipfile.ZipFile(partial, "w") as result:
            if not replacements.keys() <= set(original.namelist()):
                raise ValueError("Official AAR is missing the ARM64 runtime or JNI bridge")
            for entry in original.infolist():
                data = replacements.get(entry.filename)
                result.writestr(entry, original.read(entry) if data is None else data)
        partial.replace(output)
    finally:
        partial.unlink(missing_ok=True)
    print(f"Ready: {output}")


def main(args):
    source, build_dir = args.source.resolve(), args.build_dir.resolve()
    for name in ("sdk", "ndk", "java_home", "cmake", "ninja"):
        setattr(args, name, getattr(args, name).resolve())
    if (source / "VERSION_NUMBER").read_text().strip() != VERSION:
        raise ValueError(f"Use ONNX Runtime v{VERSION} sources")
    build_dir.mkdir(parents=True, exist_ok=True)
    config = build_dir / "operators.config"
    config.write_text(OPERATORS, encoding="utf-8")
    environment = os.environ.copy()
    environment["PATH"] = os.pathsep.join([
        str(args.cmake.parent), str(args.ninja.parent), str(args.java_home / "bin"),
        environment.get("PATH", ""),
    ])
    command = [
        sys.executable, str(source / "tools/ci_build/build.py"),
        "--build_dir", str(build_dir), "--config", "Release",
        "--android", "--android_abi", "arm64-v8a", "--android_api", "26",
        "--android_sdk_path", str(args.sdk), "--android_ndk_path", str(args.ndk),
        "--cmake_generator", "Ninja", "--cmake_path", str(args.cmake),
        "--skip_tests", "--skip_pip_install", "--skip_submodule_sync",
        "--build_shared_lib", "--disable_ml_ops", "--include_ops_by_config", str(config),
        "--compile_no_warning_as_error", "--target", "onnxruntime", "--parallel",
    ]
    if args.jobs is not None:
        command += [str(args.jobs)]
    if args.build_only:
        command += ["--build"]
    path_config = build_dir / "paths.cmake"
    path_config.write_text("".join(
        'add_compile_options("$<$<COMPILE_LANGUAGE:C,CXX>:'
        f'-ffile-prefix-map={path.as_posix()}={alias}>")\n'
        for path, alias in ((source, "onnxruntime"), (build_dir, "build"))
    ), encoding="utf-8")
    command += [
        "--cmake_extra_defines", "onnxruntime_BUILD_UNIT_TESTS=OFF",
        f"CMAKE_MAKE_PROGRAM={args.ninja}",
        f"CMAKE_PROJECT_onnxruntime_INCLUDE={path_config}",
    ]
    if args.build_only:
        subprocess.run([
            str(args.cmake), "-S", str(source / "cmake"), "-B", str(build_dir / "Release"),
            f"-DCMAKE_PROJECT_onnxruntime_INCLUDE={path_config}",
        ], env=environment, check=True)
    subprocess.run(command, env=environment, check=True)
    build = build_dir / "Release"
    host = {"win32": "windows-x86_64", "linux": "linux-x86_64", "darwin": "darwin-x86_64"}[sys.platform]
    toolchain = args.ndk / "toolchains/llvm/prebuilt" / host
    compile_jni(source, build, toolchain, args.java_home)
    base_aar = args.base_aar
    if base_aar is None:
        base_aar = build_dir / f"onnxruntime-android-{VERSION}.aar"
        if not base_aar.exists():
            partial = base_aar.with_suffix(".download")
            try:
                with urllib.request.urlopen(BASE_URL, timeout=60) as response, partial.open("wb") as output:
                    shutil.copyfileobj(response, output)
                partial.replace(base_aar)
            finally:
                partial.unlink(missing_ok=True)
    package(base_aar, build, args.output.resolve())


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", type=Path, required=True)
    parser.add_argument("--sdk", type=Path, required=True)
    parser.add_argument("--ndk", type=Path, required=True)
    parser.add_argument("--java-home", type=Path, required=True)
    parser.add_argument("--cmake", type=Path, required=True)
    parser.add_argument("--ninja", type=Path, required=True)
    parser.add_argument("--jobs", type=int)
    parser.add_argument("--base-aar", type=Path)
    parser.add_argument("--build-only", action="store_true", help="Use an existing upstream CMake configuration")
    parser.add_argument("--build-dir", type=Path, default=Path(".gradle/okulo-runtime/build"))
    parser.add_argument("--output", type=Path, default=Path(".gradle/okulo-runtime/onnxruntime-android.aar"))
    main(parser.parse_args())
