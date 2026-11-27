package com.example.scalp;

interface ITouchService {
    void destroy() = 16777114;
    void tap(int x, int y) = 1;
    void swipe(int x1, int y1, int x2, int y2, int durationMs) = 2;
}
