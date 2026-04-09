cmake_minimum_required(VERSION 3.22.1)

project("mobimath_lib")

add_library(
        mobimath_lib
        SHARED
        native-lib.cpp)

find_library(
        log-lib
        log)

target_link_libraries(
        mobimath_lib
        ${log-lib})
