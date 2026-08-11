package com.nikhil.yt.eq.data

import kotlinx.serialization.Serializable

@Serializable
enum class FilterType {
    
    PK,
    
    LSC,
    
    HSC,
    
    LPQ,
    
    HPQ
}