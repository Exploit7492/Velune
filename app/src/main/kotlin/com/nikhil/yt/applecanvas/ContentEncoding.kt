package com.nikhil.yt.applecanvas
enum class ContentEncoding { GZIP, DEFLATE, IDENTITY }
val ContentEncoding.gzip get() = ContentEncoding.GZIP
val ContentEncoding.deflate get() = ContentEncoding.DEFLATE
