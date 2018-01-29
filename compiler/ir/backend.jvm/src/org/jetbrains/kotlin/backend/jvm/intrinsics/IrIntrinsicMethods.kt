/*
 * Copyright 2010-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.kotlin.backend.jvm.intrinsics

import org.jetbrains.kotlin.descriptors.CallableMemberDescriptor
import org.jetbrains.kotlin.descriptors.PropertyAccessorDescriptor
import org.jetbrains.kotlin.ir.descriptors.IrBuiltIns
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.resolve.DescriptorUtils
import org.jetbrains.org.objectweb.asm.Type

class IrIntrinsicMethods(irBuiltIns: IrBuiltIns) {

    val intrinsics = IntrinsicMethods()

    private val irMapping = hashMapOf<CallableMemberDescriptor, IntrinsicMethod>()

    init {
        irMapping.put(irBuiltIns.eqeq, Equals(KtTokens.EQEQ))
        irMapping.put(irBuiltIns.eqeqeq, Equals(KtTokens.EQEQEQ))
        irMapping.put(irBuiltIns.eqeqIeee754Float, Ieee754Equals(Type.FLOAT_TYPE))
        irMapping.put(irBuiltIns.eqeqIeee754Double, Ieee754Equals(Type.DOUBLE_TYPE))
        irMapping.put(irBuiltIns.booleanNot, Not())

        val compare = IrCompareTo()
        irMapping.put(irBuiltIns.lt0, compare)
        irMapping.put(irBuiltIns.lteq0, compare)
        irMapping.put(irBuiltIns.gt0, compare)
        irMapping.put(irBuiltIns.gteq0, compare)

        irMapping.put(irBuiltIns.compareToIeee754Float, Ieee754CompareTo(Type.FLOAT_TYPE))
        irMapping.put(irBuiltIns.compareToIeee754Double, Ieee754CompareTo(Type.DOUBLE_TYPE))

        irMapping.put(irBuiltIns.enumValueOf, IrEnumValueOf())
        irMapping.put(irBuiltIns.noWhenBranchMatchedException, IrNoWhenBranchMatchedException())
        irMapping.put(irBuiltIns.throwNpe, ThrowNPE())
    }

    fun getIntrinsic(descriptor: CallableMemberDescriptor): IntrinsicMethod? {
        intrinsics.getIntrinsic(descriptor)?.let { return it }
        if (descriptor is PropertyAccessorDescriptor) {
            return intrinsics.getIntrinsic(DescriptorUtils.unwrapFakeOverride(descriptor.correspondingProperty))
        }
        return irMapping[descriptor.original]
    }

}
