# For more information about using CMake with Android Studio, read the
# documentation: https://d.android.com/studio/projects/add-native-code.html
cmake_minimum_required(VERSION 3.22.1)

project("kradus")

# -O3: heavy paths (30k-track normalize, bulk dedupe, recursive MP3 scan)
# must run freely even on very weak phones.
add_library(kradus SHARED native-lib.cpp)

find_library(log-lib log)

target_link_libraries(kradus ${log-lib})
