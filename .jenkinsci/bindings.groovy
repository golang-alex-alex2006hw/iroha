#!/usr/bin/env groovy

def doJavaBindings(os, buildType=Release) {
  def currentPath = sh(script: "pwd", returnStdout: true).trim()
  def commit = env.GIT_COMMIT
  def artifactsPath = sprintf('%1$s/java-bindings-%2$s-%3$s-%4$s.zip',
    [currentPath, buildType, sh(script: 'date "+%Y%m%d"', returnStdout: true).trim(), commit.substring(0,6)])
  def cmakeOptions = ""
  if (os == 'windows') {
    sh "mkdir -p /tmp/${env.GIT_COMMIT}/bindings-artifact"
    cmakeOptions = '-DCMAKE_TOOLCHAIN_FILE=/c/Users/Administrator/Downloads/vcpkg-master/vcpkg-master/scripts/buildsystems/vcpkg.cmake -G "NMake Makefiles"'
  }
  sh """
    cmake \
      -Hshared_model \
      -Bbuild \
      -DCMAKE_BUILD_TYPE=$buildType \
      -DSWIG_JAVA=ON \
      ${cmakeOptions}
  """
  sh "cmake --build build --target irohajava --config ${buildType}"
  //sh "cd build; ctest -R java -C ${buildType} --output-on-failure"
  sh "zip -j $artifactsPath build/bindings/*.java build/bindings/*.dll build/bindings/libirohajava.so"
  if (os == 'windows') {
    sh "cp $artifactsPath /tmp/${env.GIT_COMMIT}/bindings-artifact"
  }
  else {
    sh "cp $artifactsPath /tmp/bindings-artifact"
  }
  return artifactsPath
}

def doPythonBindings(os, buildType=Release) {
  def currentPath = sh(script: "pwd", returnStdout: true).trim()
  def commit = env.GIT_COMMIT
  def supportPython2 = "OFF"
  def artifactsPath = sprintf('%1$s/python-bindings-%2$s-%3$s-%4$s-%5$s.zip',
    [currentPath, env.PBVersion, buildType, sh(script: 'date "+%Y%m%d"', returnStdout: true).trim(), commit.substring(0,6)])
  def cmakeOptions = ""
  if (os == 'windows') {
    sh "mkdir -p /tmp/${env.GIT_COMMIT}/bindings-artifact"
    cmakeOptions = '-DCMAKE_TOOLCHAIN_FILE=/c/Users/Administrator/Downloads/vcpkg-master/vcpkg-master/scripts/buildsystems/vcpkg.cmake -G "NMake Makefiles"'
  }
  if (env.PBVersion == "python2") { supportPython2 = "ON" }
  sh """
    cmake \
      -Hshared_model \
      -Bbuild \
      -DCMAKE_BUILD_TYPE=$buildType \
      -DSWIG_PYTHON=ON \
      -DSUPPORT_PYTHON2=$supportPython2 \
      ${cmakeOptions}
  """
  sh "cmake --build build --target irohapy --config ${buildType}"
  sh "cmake --build build --target python_tests --config ${buildType}"
  sh "cd build; ctest -R python -C ${buildType} --output-on-failure"
  if (os == 'linux') {
    sh """
      protoc --proto_path=schema \
        --python_out=build/bindings \
        block.proto primitive.proto commands.proto queries.proto responses.proto endpoint.proto
    """
    sh """
      ${env.PBVersion} -m grpc_tools.protoc --proto_path=schema --python_out=build/bindings \
        --grpc_python_out=build/bindings endpoint.proto yac.proto ordering.proto loader.proto
    """
  }
  else if (os == 'windows') {
    sh """
      protoc --proto_path=schema \
        --proto_path=/c/Users/Administrator/Downloads/vcpkg-master/vcpkg-master/buildtrees/protobuf/src/protobuf-3.5.1-win32/include \
        --python_out=build/bindings \
        block.proto primitive.proto commands.proto queries.proto responses.proto endpoint.proto
    """
    sh """
      ${env.PBVersion} -m grpc_tools.protoc \
        --proto_path=/c/Users/Administrator/Downloads/vcpkg-master/vcpkg-master/buildtrees/protobuf/src/protobuf-3.5.1-win32/include \
        --proto_path=schema --python_out=build/bindings --grpc_python_out=build/bindings \
        endpoint.proto yac.proto ordering.proto loader.proto
    """
  }
  sh """
    zip -j $artifactsPath build/bindings/*.py build/bindings/*.dll build/bindings/*.so \
      build/bindings/*.py build/bindings/*.pyd build/bindings/*.lib build/bindings/*.dll \
      build/bindings/*.exp build/bindings/*.manifest
    """
  if (os == 'windows') {
    sh "cp $artifactsPath /tmp/${env.GIT_COMMIT}/bindings-artifact"
  }
  else {
    sh "cp $artifactsPath /tmp/bindings-artifact"
  }
  return artifactsPath
}

def doAndroidBindings(abiVersion) {
  def currentPath = sh(script: "pwd", returnStdout: true).trim()
  def commit = env.GIT_COMMIT
  def artifactsPath = sprintf('%1$s/android-bindings-%2$s-%3$s-%4$s-%5$s-%6$s.zip', 
    [currentPath, "\$PLATFORM", abiVersion, "\$BUILD_TYPE_A", sh(script: 'date "+%Y%m%d"', returnStdout: true).trim(), commit.substring(0,6)])
  sh """
    (cd /iroha; git init; git remote add origin https://github.com/hyperledger/iroha.git; \
    git fetch --depth 1 origin develop; git checkout -t origin/develop)
  """
  sh """
    . /entrypoint.sh; \
    sed -i.bak "s~find_package(JNI REQUIRED)~SET(CMAKE_SWIG_FLAGS \\\${CMAKE_SWIG_FLAGS} -package \${PACKAGE})~" /iroha/shared_model/bindings/CMakeLists.txt; \
    # TODO: might not be needed in the future
    sed -i.bak "/target_include_directories(\\\${SWIG_MODULE_irohajava_REAL_NAME} PUBLIC/,+3d" /iroha/shared_model/bindings/CMakeLists.txt; \
    sed -i.bak "s~swig_link_libraries(irohajava~swig_link_libraries(irohajava \"/protobuf/.build/lib\${PROTOBUF_LIB_NAME}.a\" \"\${NDK_PATH}/platforms/android-$abiVersion/\${ARCH}/usr/\${LIBP}/liblog.so\"~" /iroha/shared_model/bindings/CMakeLists.txt; \
    sed -i.bak "s~find_library(protobuf_LIBRARY protobuf)~find_library(protobuf_LIBRARY \${PROTOBUF_LIB_NAME})~" /iroha/cmake/Modules/Findprotobuf.cmake; \
    sed -i.bak "s~find_program(protoc_EXECUTABLE protoc~set(protoc_EXECUTABLE \"/protobuf/host_build/protoc\"~" /iroha/cmake/Modules/Findprotobuf.cmake; \
    cmake -H/iroha/shared_model -B/iroha/shared_model/build -DCMAKE_SYSTEM_NAME=Android -DCMAKE_SYSTEM_VERSION=$abiVersion -DCMAKE_ANDROID_ARCH_ABI=\$PLATFORM \
      -DANDROID_NDK=\$NDK_PATH -DCMAKE_ANDROID_STL_TYPE=c++_static -DCMAKE_BUILD_TYPE=\$BUILD_TYPE_A -DTESTING=OFF \
      -DSHARED_MODEL_DISABLE_COMPATIBILITY=ON -DSWIG_JAVA=ON -DCMAKE_PREFIX_PATH=\$DEPS_DIR
    """
  sh "cmake --build /iroha/shared_model/build --target irohajava -- -j${params.PARALLELISM}"
  sh "zip -j $artifactsPath /iroha/shared_model/build/bindings/*.java /iroha/shared_model/build/bindings/libirohajava.so"
  sh "cp $artifactsPath /tmp/bindings-artifact"
  return artifactsPath
}

return this
