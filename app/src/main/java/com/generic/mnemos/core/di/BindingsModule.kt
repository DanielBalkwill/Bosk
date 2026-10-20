package com.generic.mnemos.core.di

import com.generic.mnemos.core.ai.KnowledgeProfileManager
import com.generic.mnemos.core.ai.KnowledgeProfileManagerImpl
import com.generic.mnemos.core.ai.llm.AnthropicProvider
import com.generic.mnemos.core.ai.llm.GeminiNanoProvider
import com.generic.mnemos.core.ai.llm.GeminiProvider
import com.generic.mnemos.core.ai.llm.LlmClient
import com.generic.mnemos.core.ai.llm.LlmProvider
import com.generic.mnemos.core.ai.llm.LlmRouter
import com.generic.mnemos.core.ai.llm.OpenAiCompatibleProvider
import com.generic.mnemos.core.ai.llm.OpenAiProvider
import com.generic.mnemos.core.ai.llm.ProviderId
import com.generic.mnemos.core.ai.llm.ProviderKey
import com.generic.mnemos.core.curriculum.CurriculumProvider
import com.generic.mnemos.core.curriculum.CurriculumProviderImpl
import com.generic.mnemos.core.graph.GraphRepository
import com.generic.mnemos.core.graph.GraphRepositoryImpl
import com.generic.mnemos.core.notes.NotesRepository
import com.generic.mnemos.core.notes.NotesRepositoryImpl
import com.generic.mnemos.core.review.ReviewRepository
import com.generic.mnemos.core.review.ReviewRepositoryImpl
import com.generic.mnemos.core.settings.SettingsRepository
import com.generic.mnemos.core.settings.SettingsRepositoryImpl
import com.generic.mnemos.core.tts.TtsQueueEngine
import com.generic.mnemos.core.tts.TtsQueueEngineImpl
import dagger.Binds
import dagger.Module
import dagger.multibindings.IntoMap
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Binds interfaces to their implementations. */
@Module
@InstallIn(SingletonComponent::class)
abstract class BindingsModule {

    @Binds
    @Singleton
    abstract fun bindNotesRepository(impl: NotesRepositoryImpl): NotesRepository

    // The app injects LlmClient; LlmRouter forwards to whichever provider is active.
    @Binds
    @Singleton
    abstract fun bindLlmClient(impl: LlmRouter): LlmClient

    // Provider multibinding map: ProviderId -> LlmProvider (consumed by LlmRouter).
    @Binds
    @IntoMap
    @ProviderKey(ProviderId.GEMINI)
    abstract fun bindGeminiProvider(impl: GeminiProvider): LlmProvider

    @Binds
    @IntoMap
    @ProviderKey(ProviderId.OPENAI)
    abstract fun bindOpenAiProvider(impl: OpenAiProvider): LlmProvider

    @Binds
    @IntoMap
    @ProviderKey(ProviderId.ANTHROPIC)
    abstract fun bindAnthropicProvider(impl: AnthropicProvider): LlmProvider

    @Binds
    @IntoMap
    @ProviderKey(ProviderId.OPENAI_COMPAT)
    abstract fun bindOpenAiCompatibleProvider(impl: OpenAiCompatibleProvider): LlmProvider

    @Binds
    @IntoMap
    @ProviderKey(ProviderId.NANO)
    abstract fun bindNanoProvider(impl: GeminiNanoProvider): LlmProvider

    @Binds
    @Singleton
    abstract fun bindKnowledgeProfileManager(impl: KnowledgeProfileManagerImpl): KnowledgeProfileManager

    @Binds
    @Singleton
    abstract fun bindGraphRepository(impl: GraphRepositoryImpl): GraphRepository

    @Binds
    @Singleton
    abstract fun bindReviewRepository(impl: ReviewRepositoryImpl): ReviewRepository

    @Binds
    @Singleton
    abstract fun bindSettingsRepository(impl: SettingsRepositoryImpl): SettingsRepository

    @Binds
    @Singleton
    abstract fun bindTtsQueueEngine(impl: TtsQueueEngineImpl): TtsQueueEngine

    @Binds
    @Singleton
    abstract fun bindCurriculumProvider(impl: CurriculumProviderImpl): CurriculumProvider
}
