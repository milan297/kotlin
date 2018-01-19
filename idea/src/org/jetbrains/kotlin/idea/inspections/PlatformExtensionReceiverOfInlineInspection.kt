/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.inspections

import com.intellij.codeInspection.IntentionWrapper
import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import org.jetbrains.kotlin.config.LanguageFeature
import org.jetbrains.kotlin.descriptors.FunctionDescriptor
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.project.languageVersionSettings
import org.jetbrains.kotlin.idea.quickfix.AddExclExclCallFix
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtVisitorVoid
import org.jetbrains.kotlin.resolve.calls.callUtil.getResolvedCall
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode
import org.jetbrains.kotlin.types.isNullabilityFlexible

class PlatformExtensionReceiverOfInlineInspection : AbstractKotlinInspection() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean, session: LocalInspectionToolSession) =
        object : KtVisitorVoid() {
            override fun visitDotQualifiedExpression(expression: KtDotQualifiedExpression) {
                super.visitDotQualifiedExpression(expression)

                if (!expression.languageVersionSettings.supportsFeature(LanguageFeature.NullabilityAssertionOnExtensionReceiver)) {
                    return
                }

                val context = expression.analyze(BodyResolveMode.PARTIAL)
                val resolvedCall = expression.getResolvedCall(context) ?: return
                val extensionReceiverType = resolvedCall.extensionReceiver?.type ?: return
                if (!extensionReceiverType.isNullabilityFlexible()) return
                val descriptor = resolvedCall.resultingDescriptor as? FunctionDescriptor ?: return
                if (!descriptor.isInline) return

                val receiverExpression = expression.receiverExpression
                holder.registerProblem(
                    receiverExpression,
                    "Call of inline function with nullable extension receiver can provoke NPE in Kotlin 1.2+",
                    ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                    IntentionWrapper(AddExclExclCallFix(receiverExpression), receiverExpression.containingKtFile)
                )
            }
        }
}