package app.bosk.core.di

import app.bosk.core.ai.KnowledgeProfileManager
import app.bosk.core.ai.KnowledgeProfileManagerImpl
import app.bosk.core.ai.llm.AnthropicProvider
import app.bosk.core.ai.llm.GeminiNanoProvider
import app.bosk.core.ai.llm.GeminiProvider
import app.bosk.core.ai.llm.LlmClient
import app.bosk.core.ai.llm.LlmProvider
import app.bosk.core.ai.llm.LlmRouter
import app.bosk.core.ai.llm.OpenAiCompatibleProvider
import app.bosk.core.ai.llm.OpenAiProvider
import app.bosk.core.ai.llm.ProviderId
import app.bosk.core.ai.llm.ProviderKey
import app.bosk.core.curriculum.CurriculumProvider
import app.bosk.core.curriculum.CurriculumProviderImpl
import app.bosk.core.graph.GraphRepository
import app.bosk.core.graph.GraphRepositoryImpl
import app.bosk.core.notes.NotesRepository
import app.bosk.core.notes.NotesRepositoryImpl
import app.bosk.core.review.ReviewRepository
import app.bosk.core.review.ReviewRepositoryImpl
import app.bosk.core.settings.SettingsRepository
import app.bosk.core.settings.SettingsRepositoryImpl
import app.bosk.core.tts.TtsQueueEngine
import app.bosk.core.tts.TtsQueueEngineImpl
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
