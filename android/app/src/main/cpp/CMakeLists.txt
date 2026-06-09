cmake_minimum_required(VERSION 3.10.2)

project("virtual_engine")

add_library(
        virtual_engine
        SHARED
        virtual_engine.cpp)

find_library(
        log-lib
        log)

target_link_libraries(
        virtual_engine
        ${log-lib})
