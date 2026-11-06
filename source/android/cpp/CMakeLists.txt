cmake_minimum_required(VERSION 3.22.1)
project(blocker_engine LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

# Aggressive optimizations for maximum throughput, microsecond latency and binary size reduction
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -O3 -flto -fvisibility=hidden -fvisibility-inlines-hidden -fno-exceptions -fno-rtti -fdata-sections -ffunction-sections")
set(CMAKE_SHARED_LINKER_FLAGS "${CMAKE_SHARED_LINKER_FLAGS} -Wl,--gc-sections -Wl,--exclude-libs,ALL -flto")

if(ANDROID_ABI STREQUAL "armeabi-v7a")
    set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -mfpu=neon -mfloat-abi=softfp -mthumb")
endif()

add_library(blocker_engine SHARED
    blocker_engine.cpp
)

find_library(log-lib log)

target_link_libraries(blocker_engine
    ${log-lib}
)
