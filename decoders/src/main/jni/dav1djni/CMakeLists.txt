cmake_minimum_required(VERSION 3.22.1)
project(dav1dJNI C CXX)

set(DAV1D_OUT "${THIRD_PARTY_OUT}/dav1d/${ANDROID_ABI}")

set(BUILD_TESTING OFF CACHE BOOL "" FORCE)
set(BUILD_EXECUTABLE OFF CACHE BOOL "" FORCE)
set(ENABLE_INSTALL OFF CACHE BOOL "" FORCE)
add_subdirectory("${CMAKE_CURRENT_SOURCE_DIR}/../third_party/cpu_features" "${CMAKE_CURRENT_BINARY_DIR}/cpu_features")

add_library(dav1d STATIC IMPORTED)
set_target_properties(dav1d PROPERTIES IMPORTED_LOCATION "${DAV1D_OUT}/libdav1d.a")

add_library(dav1dJNI SHARED dav1d_jni.cc cpu_info.cc)
target_include_directories(dav1dJNI PRIVATE "${DAV1D_OUT}/include" "${CMAKE_CURRENT_SOURCE_DIR}")
target_link_libraries(dav1dJNI PRIVATE dav1d cpu_features android log)
if(ANDROID_ABI STREQUAL "armeabi-v7a")
  target_compile_options(dav1dJNI PRIVATE -mfpu=neon -marm -fPIC)
endif()
