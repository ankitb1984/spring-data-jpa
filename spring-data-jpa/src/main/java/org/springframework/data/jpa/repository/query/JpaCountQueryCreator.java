/*
 * Copyright 2008-2023 the original author or authors.
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
package org.springframework.data.jpa.repository.query;

import org.springframework.data.domain.Sort;
import org.springframework.data.repository.query.ReturnedType;
import org.springframework.data.repository.query.parser.PartTree;
import org.springframework.lang.Nullable;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

/**
 * Special {@link JpaQueryCreator} that creates a count projecting query.
 *
 * @author Oliver Gierke
 * @author Marc Lefrançois
 * @author Mark Paluch
 * @author Greg Turnquist
 * @author Christian Wörz
 */
public class JpaCountQueryCreator extends JpaQueryCreator {

	private boolean distinct;

	/**
	 * Creates a new {@link JpaCountQueryCreator}.
	 *
	 * @param tree
	 * @param type
	 * @param builder
	 * @param provider
	 */
	public JpaCountQueryCreator(PartTree tree, ReturnedType type, CriteriaBuilder builder,
			ParameterMetadataProvider provider) {

		super(tree, type, builder, provider);

		this.distinct = tree.isDistinct();
	}

	@Override
	protected CriteriaQuery<?> createCriteriaQuery(CriteriaBuilder builder, ReturnedType type) {

		return builder.createQuery(Long.class);
	}

	@Override
	@SuppressWarnings("unchecked")
	protected CriteriaQuery<?> complete(@Nullable Predicate predicate, Sort sort, CriteriaQuery<?> query,
			CriteriaBuilder builder, Root<?> root) {

		CriteriaQuery<?> select = query.select(getCountQuery(query, builder, root));
		return predicate == null ? select : select.where(predicate);
	}

	@SuppressWarnings("rawtypes")
	private Expression getCountQuery(CriteriaQuery<?> query, CriteriaBuilder builder, Root<?> root) {
		return distinct ? builder.countDistinct(root) : builder.count(root);
	}
}
