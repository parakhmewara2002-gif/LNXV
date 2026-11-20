cmake_minimum_required(VERSION 3.22.1)
project(lnxv_decoders C CXX)

# Page size 16 KB for Android 15+ devices (NDK r26 does not default to it).
set(CMAKE_SHARED_LINKER_FLAGS "${CMAKE_SHARED_LINKER_FLAGS} -Wl,-z,max-page-size=16384")

set(THIRD_PARTY_OUT "${CMAKE_CURRENT_SOURCE_DIR}/third_party/out")

if(EXISTS "${THIRD_PARTY_OUT}/ffmpeg/${ANDROID_ABI}/lib")
  add_subdirectory(ffmpegjni)
endif()
if(EXISTS "${THIRD_PARTY_OUT}/dav1d/${ANDROID_ABI}/libdav1d.a")
  add_subdirectory(dav1djni)
endif()
