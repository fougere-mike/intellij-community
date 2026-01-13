// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.ide.brs.analyzer

import org.jetbrains.kotlin.analyzer.*
import org.jetbrains.kotlin.brs.resolve.BrsPlatformAnalyzerServices
import org.jetbrains.kotlin.caches.resolve.resolution
import org.jetbrains.kotlin.config.LanguageVersionSettings
import org.jetbrains.kotlin.container.get
import org.jetbrains.kotlin.context.ModuleContext
import org.jetbrains.kotlin.descriptors.impl.CompositePackageFragmentProvider
import org.jetbrains.kotlin.descriptors.impl.ModuleDescriptorImpl
import org.jetbrains.kotlin.frontend.di.createContainerForLazyResolve
import org.jetbrains.kotlin.idea.project.IdeaAbsentDescriptorHandler
import org.jetbrains.kotlin.platform.TargetPlatform
import org.jetbrains.kotlin.platform.brs.BrsPlatforms
import org.jetbrains.kotlin.platform.idePlatformKind
import org.jetbrains.kotlin.resolve.CodeAnalyzerInitializer
import org.jetbrains.kotlin.resolve.SealedClassInheritorsProvider
import org.jetbrains.kotlin.resolve.TargetEnvironment
import org.jetbrains.kotlin.resolve.lazy.AbsentDescriptorHandler
import org.jetbrains.kotlin.resolve.lazy.ResolveSession
import org.jetbrains.kotlin.resolve.lazy.declarations.DeclarationProviderFactoryService.Companion.createDeclarationProviderFactory
import org.jetbrains.kotlin.resolve.scopes.optimization.OptimizingOptions

class BrsResolverForModuleFactory(
    private val platformAnalysisParameters: PlatformAnalysisParameters,
    private val targetEnvironment: TargetEnvironment,
    private val targetPlatform: TargetPlatform
) : ResolverForModuleFactory() {
    override fun <M : ModuleInfo> createResolverForModule(
        moduleDescriptor: ModuleDescriptorImpl,
        moduleContext: ModuleContext,
        moduleContent: ModuleContent<M>,
        resolverForProject: ResolverForProject<M>,
        languageVersionSettings: LanguageVersionSettings,
        sealedInheritorsProvider: SealedClassInheritorsProvider,
        resolveOptimizingOptions: OptimizingOptions?,
        absentDescriptorHandlerClass: Class<out AbsentDescriptorHandler>?
    ): ResolverForModule {

        val declarationProviderFactory = createDeclarationProviderFactory(
            moduleContext.project,
            moduleContext.storageManager,
            moduleContent.syntheticFiles,
            moduleContent.moduleContentScope,
            moduleContent.moduleInfo
        )

        val container = createContainerForLazyResolve(
            moduleContext,
            declarationProviderFactory,
            CodeAnalyzerInitializer.getInstance(moduleContext.project).createTrace(),
            moduleDescriptor.platform!!,
            BrsPlatformAnalyzerServices,
            targetEnvironment,
            languageVersionSettings,
            IdeaAbsentDescriptorHandler::class.java
        )

        var packageFragmentProvider = container.get<ResolveSession>().packageFragmentProvider

        val klibPackageFragmentProvider =
            BrsPlatforms.defaultBrsPlatform.idePlatformKind.resolution.createKlibPackageFragmentProvider(
                moduleContent.moduleInfo,
                moduleContext.storageManager,
                languageVersionSettings,
                moduleDescriptor
            )

        if (klibPackageFragmentProvider != null) {
            packageFragmentProvider = CompositePackageFragmentProvider(
                listOf(packageFragmentProvider, klibPackageFragmentProvider),
                "CompositeProvider@BrsResolver for $moduleDescriptor"
            )
        }

        return ResolverForModule(packageFragmentProvider, container)
    }
}
