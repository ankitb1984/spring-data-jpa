/*
 * Copyright 2022 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.data.jpa.aot;

import java.util.Arrays;

import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.aot.hint.TypeReference;
import org.springframework.beans.factory.aspectj.AnnotationBeanConfigurerAspect;
import org.springframework.data.jpa.domain.support.AuditingBeanFactoryPostProcessor;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import org.springframework.data.jpa.repository.support.SimpleJpaRepository;
import org.springframework.lang.Nullable;

/**
 * @author Christoph Strobl
 * @since 3.0
 */
public class DataJpaRuntimeHints implements RuntimeHintsRegistrar {

	@Override
	public void registerHints(RuntimeHints hints, @Nullable ClassLoader classLoader) {

		hints.proxies().registerJdkProxy(org.springframework.data.jpa.repository.support.CrudMethodMetadata.class,
				org.springframework.aop.SpringProxy.class, org.springframework.aop.framework.Advised.class,
				org.springframework.core.DecoratingProxy.class);
		hints.reflection().registerTypes(
				Arrays.asList(TypeReference.of(AnnotationBeanConfigurerAspect.class),
						TypeReference.of(AuditingBeanFactoryPostProcessor.class), TypeReference.of(AuditingEntityListener.class)),
				hint -> hint.withMembers(MemberCategory.INVOKE_DECLARED_CONSTRUCTORS, MemberCategory.INVOKE_DECLARED_METHODS));
		hints.reflection().registerTypes(Arrays.asList(TypeReference.of(SimpleJpaRepository.class)),
				hint -> hint.withMembers(MemberCategory.INVOKE_DECLARED_CONSTRUCTORS, MemberCategory.INVOKE_PUBLIC_METHODS));
	}
}
