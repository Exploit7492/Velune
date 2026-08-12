/*
 * Velune - by Nikhil
 * Nikhil
 * Licensed Under GPL-3.0
 */



package com.nikhil.yt.di

import com.nikhil.yt.eq.EqualizerService
import com.nikhil.yt.eq.data.EQProfileRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface EqEntryPoint {
    fun equalizerService(): EqualizerService
    fun eqProfileRepository(): EQProfileRepository
}
