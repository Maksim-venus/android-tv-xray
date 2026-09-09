cmake_minimum_required(VERSION 3.22.1)
project(xray_stub)

add_library(xray_stub SHARED xray_stub.cpp)
find_library(log-lib log)
target_link_libraries(xray_stub ${log-lib})
