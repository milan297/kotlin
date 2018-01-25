/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.resolve.checkers

import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.builtins.KotlinBuiltIns
import org.jetbrains.kotlin.descriptors.FunctionDescriptor
import org.jetbrains.kotlin.descriptors.PackageFragmentDescriptor
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.calls.checkers.CallChecker
import org.jetbrains.kotlin.resolve.calls.checkers.CallCheckerContext
import org.jetbrains.kotlin.resolve.calls.model.ResolvedCall
import org.jetbrains.kotlin.resolve.calls.smartcasts.DataFlowValueFactory
import org.jetbrains.kotlin.resolve.calls.tower.transformToReceiverWithSmartCastInfo
import org.jetbrains.kotlin.resolve.scopes.receivers.ReceiverValue
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.typeUtil.*
import org.jetbrains.kotlin.util.OperatorNameConventions
import org.jetbrains.kotlin.utils.addToStdlib.firstNotNullResult

object FloatingPointComparisonCallChecker : CallChecker {

    override fun check(resolvedCall: ResolvedCall<*>, reportOn: PsiElement, context: CallCheckerContext) {
        // IEEE 754 floating point comparisons only take part in binary operator convention resolution
        if (resolvedCall.call.callElement !is KtBinaryExpression) return

        with(context) {
            val callee = resolvedCall.resultingDescriptor as? FunctionDescriptor ?: return
            if (!callee.isSubjectToFloatingPointComparisonRules()) return

            val receiver = resolvedCall.dispatchReceiver ?: return

            val resolvedArg0 = resolvedCall.valueArgumentsByIndex?.get(0) ?: return
            val arg0Expr = resolvedArg0.arguments.singleOrNull()?.getArgumentExpression() ?: return

            val receiverType = getInferredFloatingPointType(receiver) ?: return
            val argType = getInferredFloatingPointType(arg0Expr) ?: return

            if (argType == receiverType) {
                trace.record(BindingContext.FLOATING_POINT_COMPARISON_TYPE, resolvedCall, argType)
            }
        }
    }

    private fun FunctionDescriptor.isSubjectToFloatingPointComparisonRules(): Boolean =
        isEquals() || isCompareToWithFloatingPoint()

    private fun FunctionDescriptor.isEquals(): Boolean {
        val singleParameterType = valueParameters.singleOrNull()?.type ?: return false
        val returnType = returnType ?: return false

        if (name != OperatorNameConventions.EQUALS) return false
        if (!returnType.isBoolean()) return false
        if (!singleParameterType.isNullableAny()) return false
        return true
    }

    private fun FunctionDescriptor.isCompareToWithFloatingPoint(): Boolean {
        val ownerType = dispatchReceiverParameter?.type ?: return false
        val singleParameterType = valueParameters.singleOrNull()?.type ?: return false
        val returnType = returnType ?: return false

        if (name != OperatorNameConventions.COMPARE_TO) return false
        if (!returnType.isInt()) return false

        if (ownerType.isFloatingPointType() && singleParameterType.isPrimitiveNumberType()) return true
        if (ownerType.isPrimitiveNumberType() && singleParameterType.isFloatingPointType()) return true
        if (ownerType.isComparableOnFloatingPointType() && singleParameterType.isFloatingPointType()) return true
        return false
    }

    private fun KotlinType.isFloatingPointType() =
        KotlinBuiltIns.isFloat(this) || KotlinBuiltIns.isDouble(this)

    private fun KotlinType.isComparableOnFloatingPointType(): Boolean {
        val typeDescriptor = constructor.declarationDescriptor ?: return false
        if (typeDescriptor.name != Name.identifier("Comparable")) return false
        val containingPackage = typeDescriptor.containingDeclaration as? PackageFragmentDescriptor ?: return false
        if (containingPackage.fqName.asString() != "kotlin") return false
        val singleTypeArgument = arguments.singleOrNull()?.type ?: return false
        return singleTypeArgument.isFloatingPointType()
    }

    private val CallCheckerContext.containingDeclaration
        get() = resolutionContext.scope.ownerDescriptor

    private fun CallCheckerContext.getInferredFloatingPointType(expression: KtExpression): KotlinType? {
        val type = trace.bindingContext.getType(expression) ?: return null
        type.getFloatingPointTypeOrNull()?.let { return it }

        val dataFlowValue = DataFlowValueFactory.createDataFlowValue(expression, type, trace.bindingContext, containingDeclaration)
        val dataFlowInfo = trace.get(BindingContext.EXPRESSION_TYPE_INFO, expression)?.dataFlowInfo ?: return null
        val stableTypes = dataFlowInfo.getStableTypes(dataFlowValue, languageVersionSettings)
        return stableTypes.firstNotNullResult { it.getFloatingPointTypeOrNull() }
    }

    private fun CallCheckerContext.getInferredFloatingPointType(receiverValue: ReceiverValue): KotlinType? {
        receiverValue.type.getFloatingPointTypeOrNull()?.let { return it }

        val withSmartCastInfo = resolutionContext.transformToReceiverWithSmartCastInfo(receiverValue)
        val stableTypes =
            if (withSmartCastInfo.isStable)
                withSmartCastInfo.possibleTypes + receiverValue.type
            else
                setOf(receiverValue.type)

        return stableTypes.firstNotNullResult { it.getFloatingPointTypeOrNull() }
    }

    private fun KotlinType.getFloatingPointTypeOrNull(): KotlinType? =
        when {
            KotlinBuiltIns.isFloatOrNullableFloat(this) -> builtIns.floatType
            KotlinBuiltIns.isDoubleOrNullableDouble(this) -> builtIns.doubleType
            else -> null
        }

}