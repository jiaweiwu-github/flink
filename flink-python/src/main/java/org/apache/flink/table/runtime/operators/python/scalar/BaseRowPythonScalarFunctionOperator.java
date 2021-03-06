/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.flink.table.runtime.operators.python.scalar;

import org.apache.flink.annotation.Internal;
import org.apache.flink.api.common.typeutils.TypeSerializer;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.python.PythonFunctionRunner;
import org.apache.flink.python.env.PythonEnvironmentManager;
import org.apache.flink.table.dataformat.BaseRow;
import org.apache.flink.table.functions.ScalarFunction;
import org.apache.flink.table.functions.python.PythonFunctionInfo;
import org.apache.flink.table.runtime.runners.python.scalar.BaseRowPythonScalarFunctionRunner;
import org.apache.flink.table.runtime.typeutils.PythonTypeUtils;
import org.apache.flink.table.types.logical.RowType;

import org.apache.beam.sdk.fn.data.FnDataReceiver;

import java.io.IOException;

/**
 * The Python {@link ScalarFunction} operator for the blink planner.
 */
@Internal
public class BaseRowPythonScalarFunctionOperator extends AbstractBaseRowPythonScalarFunctionOperator {

	private static final long serialVersionUID = 1L;

	/**
	 * The TypeSerializer for udf execution results.
	 */
	private transient TypeSerializer<BaseRow> udfOutputTypeSerializer;

	public BaseRowPythonScalarFunctionOperator(
		Configuration config,
		PythonFunctionInfo[] scalarFunctions,
		RowType inputType,
		RowType outputType,
		int[] udfInputOffsets,
		int[] forwardedFields) {
		super(config, scalarFunctions, inputType, outputType, udfInputOffsets, forwardedFields);
	}

	@Override
	@SuppressWarnings("unchecked")
	public void open() throws Exception {
		super.open();
		udfOutputTypeSerializer = PythonTypeUtils.toBlinkTypeSerializer(userDefinedFunctionOutputType);
	}

	@Override
	@SuppressWarnings("ConstantConditions")
	public void emitResults() throws IOException {
		byte[] rawUdfResult;
		while ((rawUdfResult = userDefinedFunctionResultQueue.poll()) != null) {
			BaseRow input = forwardedInputQueue.poll();
			reuseJoinedRow.setHeader(input.getHeader());
			bais.setBuffer(rawUdfResult, 0, rawUdfResult.length);
			BaseRow udfResult = udfOutputTypeSerializer.deserialize(baisWrapper);
			baseRowWrapper.collect(reuseJoinedRow.replace(input, udfResult));
		}
	}

	@Override
	public PythonFunctionRunner<BaseRow> createPythonFunctionRunner(
			FnDataReceiver<byte[]> resultReceiver,
			PythonEnvironmentManager pythonEnvironmentManager) {
		return new BaseRowPythonScalarFunctionRunner(
			getRuntimeContext().getTaskName(),
			resultReceiver,
			scalarFunctions,
			pythonEnvironmentManager,
			userDefinedFunctionInputType,
			userDefinedFunctionOutputType);
	}
}
