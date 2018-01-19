/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.inspections

import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import org.jetbrains.kotlin.config.LanguageFeature
import org.jetbrains.kotlin.idea.caches.resolve.analyzeFully
import org.jetbrains.kotlin.idea.project.languageVersionSettings
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.anyDescendantOfType
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.calls.callUtil.getResolvedCall

class RedundantNotNullExtensionReceiverOfInlineInspection : AbstractKotlinInspection() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean, session: LocalInspectionToolSession) =
        object : KtVisitorVoid() {
            override fun visitNamedFunction(function: KtNamedFunction) {
                super.visitNamedFunction(function)

                val receiverTypeReference = function.receiverTypeReference ?: return
                if (!function.hasModifier(KtTokens.INLINE_KEYWORD) || !function.hasBody()) return
                if (!function.languageVersionSettings.supportsFeature(LanguageFeature.NullabilityAssertionOnExtensionReceiver)) {
                    return
                }

                val context = function.analyzeFully()
                val functionDescriptor = context[BindingContext.FUNCTION, function] ?: return
                val receiverParameter = functionDescriptor.extensionReceiverParameter ?: return
                val receiverValue = receiverParameter.value
                val receiverType = receiverParameter.type
                if (receiverType.isMarkedNullable) return

                if (function.anyDescendantOfType<KtExpression> {
                        when (it) {
                            is KtDotQualifiedExpression -> {
                                val resolvedCall = it.getResolvedCall(context)
                                val descriptor = resolvedCall?.resultingDescriptor
                                val receiverExpression = it.receiverExpression
                                resolvedCall != null &&
                                        descriptor?.extensionReceiverParameter?.type?.isMarkedNullable != true &&
                                        receiverExpression is KtThisExpression
                            }
                            is KtNameReferenceExpression -> {
                                val resolvedCall = it.getResolvedCall(context)
                                val descriptor = resolvedCall?.resultingDescriptor
                                it.parent !is KtThisExpression &&
                                        it.parent.parent !is KtQualifiedExpression &&
                                        resolvedCall != null &&
                                        descriptor?.extensionReceiverParameter?.type?.isMarkedNullable != true &&
                                        (resolvedCall.dispatchReceiver == receiverValue || resolvedCall.extensionReceiver == receiverValue)

                            }
                            is KtThisExpression -> {
                                val expectedType = context[BindingContext.EXPECTED_EXPRESSION_TYPE, it]
                                it.parent !is KtDotQualifiedExpression &&
                                        expectedType != null && !expectedType.isMarkedNullable
                            }
                            is KtForExpression -> {
                                it.loopRange is KtThisExpression
                            }
                            is KtBinaryExpressionWithTypeRHS -> {
                                it.left is KtThisExpression &&
                                        it.operationReference.getReferencedNameElementType() == KtTokens.AS_KEYWORD
                            }
                            else -> false
                        }
                    }) return

                holder.registerProblem(
                    receiverTypeReference,
                    "This type probably can be changed to nullable",
                    ProblemHighlightType.GENERIC_ERROR_OR_WARNING
                )
            }
        }
}