cmake_minimum_required(VERSION 3.22.1)
project(ffmpegJNI C CXX)

set(FFMPEG_OUT "${THIRD_PARTY_OUT}/ffmpeg/${ANDROID_ABI}")

# FFmpeg is linked dynamically (LGPL): the .so files are shipped next to libffmpegJNI.so and can be replaced.
foreach(lib avutil swresample avcodec)
  add_library(${lib} SHARED IMPORTED)
  set_target_properties(${lib} PROPERTIES IMPORTED_LOCATION "${FFMPEG_OUT}/lib/lib${lib}.so")
endforeach()

add_library(ffmpegJNI SHARED ffmpeg_jni.cc)
target_include_directories(ffmpegJNI PRIVATE "${FFMPEG_OUT}/include")
target_link_libraries(ffmpegJNI PRIVATE avcodec swresample avutil android log)
if(ANDROID_ABI STREQUAL "arm64-v8a")
  target_link_options(ffmpegJNI PRIVATE -Wl,-Bsymbolic)
endif()
