/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.psi2ir.generators

import org.jetbrains.kotlin.builtins.KotlinBuiltIns
import org.jetbrains.kotlin.ir.symbols.IrFunctionSymbol
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.calls.model.ResolvedCall
import org.jetbrains.kotlin.util.OperatorNameConventions

fun GeneratorContext.remapCalleeByFloatingPointComparisonRules(call: ResolvedCall<*>): IrFunctionSymbol? {
    val calleeName = call.resultingDescriptor?.name ?: return null
    val fpComparisonType = bindingContext[BindingContext.FLOATING_POINT_COMPARISON_TYPE, call] ?: return null

    return when (calleeName) {
        OperatorNameConventions.EQUALS ->
            when {
                KotlinBuiltIns.isFloat(fpComparisonType) -> irBuiltIns.eqeqIeee754FloatSymbol
                KotlinBuiltIns.isDouble(fpComparisonType) -> irBuiltIns.eqeqIeee754DoubleSymbol
                else -> throw AssertionError("Unexpected FLOATING_POINT_COMPARISON_TYPE: $fpComparisonType")
            }
        OperatorNameConventions.COMPARE_TO ->
            when {
                KotlinBuiltIns.isFloat(fpComparisonType) -> irBuiltIns.compareToIeee754FloatSymbol
                KotlinBuiltIns.isDouble(fpComparisonType) -> irBuiltIns.compareToIeee754DoubleSymbol
                else -> throw AssertionError("Unexpected FLOATING_POINT_COMPARISON_TYPE: $fpComparisonType")
            }
        else ->
            throw AssertionError("Unexpected callee for a resolved call marked with FLOATING_POINT_COMPARISON_TYPE: '$calleeName'")
    }
}