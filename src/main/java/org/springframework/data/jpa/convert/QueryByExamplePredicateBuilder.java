/*
 * Copyright 2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.data.jpa.convert;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.Expression;
import javax.persistence.criteria.From;
import javax.persistence.criteria.Path;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;
import javax.persistence.metamodel.Attribute;
import javax.persistence.metamodel.Attribute.PersistentAttributeType;
import javax.persistence.metamodel.ManagedType;
import javax.persistence.metamodel.SingularAttribute;

import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.data.domain.Example;
import org.springframework.data.domain.Example.NullHandler;
import org.springframework.data.util.DirectFieldAccessFallbackBeanWrapper;
import org.springframework.orm.jpa.JpaSystemException;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

/**
 * {@link QueryByExamplePredicateBuilder} creates a single {@link CriteriaBuilder#and(Predicate...)} combined
 * {@link Predicate} for a given {@link Example}. <br />
 * The builder includes any {@link SingularAttribute} of the {@link Example#getProbe()} applying {@link String} and
 * {@literal null} matching strategies configured on the {@link Example}. Ignored paths are no matter of their actual
 * value not considered. <br />
 * 
 * @author Christoph Strobl
 * @since 1.10
 */
public class QueryByExamplePredicateBuilder {

	private static final Set<PersistentAttributeType> ASSOCIATION_TYPES;

	static {
		ASSOCIATION_TYPES = new HashSet<PersistentAttributeType>(Arrays.asList(PersistentAttributeType.MANY_TO_MANY,
				PersistentAttributeType.MANY_TO_ONE, PersistentAttributeType.ONE_TO_MANY, PersistentAttributeType.ONE_TO_ONE));
	}

	/**
	 * Extract the {@link Predicate} representing the {@link Example}.
	 * 
	 * @param root must not be {@literal null}.
	 * @param cb must not be {@literal null}.
	 * @param example must not be {@literal null}.
	 * @return never {@literal null}.
	 */
	public static <T> Predicate getPredicate(Root<T> root, CriteriaBuilder cb, Example<T> example) {

		Assert.notNull(root, "Root must not be null!");
		Assert.notNull(cb, "CriteriaBuilder must not be null!");
		Assert.notNull(example, "Root must not be null!");

		List<Predicate> predicates = getPredicates("", cb, root, root.getModel(), example.getSampleObject(), example,
				new PathNode("root", null, example.getSampleObject()));

		if (predicates.isEmpty()) {
			return cb.isTrue(cb.literal(false));
		}

		if (predicates.size() == 1) {
			return predicates.iterator().next();
		}

		return cb.and(predicates.toArray(new Predicate[predicates.size()]));
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	static List<Predicate> getPredicates(String path, CriteriaBuilder cb, Path<?> from, ManagedType<?> type,
			Object value, Example<?> example, PathNode currentNode) {

		List<Predicate> predicates = new ArrayList<Predicate>();
		DirectFieldAccessFallbackBeanWrapper beanWrapper = new DirectFieldAccessFallbackBeanWrapper(value);

		for (SingularAttribute attribute : type.getSingularAttributes()) {

			String currentPath = !StringUtils.hasText(path) ? attribute.getName() : path + "." + attribute.getName();

			if (example.isIgnoredPath(currentPath)) {
				continue;
			}

			Object attributeValue = example.getValueTransformerForPath(currentPath).convert(
					beanWrapper.getPropertyValue(attribute.getName()));

			if (attributeValue == null) {

				if (example.getNullHandler().equals(NullHandler.INCLUDE)) {
					predicates.add(cb.isNull(from.get(attribute)));
				}
				continue;
			}

			if (attribute.getPersistentAttributeType().equals(PersistentAttributeType.EMBEDDED)) {

				predicates.addAll(getPredicates(currentPath, cb, from.get(attribute.getName()),
						(ManagedType<?>) attribute.getType(), attributeValue, example, currentNode));
				continue;
			}

			if (isAssociation(attribute)) {

				if (!(from instanceof From)) {
					throw new JpaSystemException(new IllegalArgumentException(String.format(
							"Unexpected path type for %s. Found % where From.class was expected.", currentPath, from)));
				}

				PathNode node = currentNode.add(attribute.getName(), attributeValue);
				if (node.spansCycle()) {
					throw new InvalidDataAccessApiUsageException(String.format(
							"Path '%s' from root %s must not span a cyclic property reference!\r\n%s", currentPath,
							ClassUtils.getShortName(example.getSampleType()), node));
				}

				predicates.addAll(getPredicates(currentPath, cb, ((From<?, ?>) from).join(attribute.getName()),
						(ManagedType<?>) attribute.getType(), attributeValue, example, node));

				continue;
			}

			if (attribute.getJavaType().equals(String.class)) {

				Expression<String> expression = from.get(attribute);
				if (example.isIgnoreCaseForPath(currentPath)) {
					expression = cb.lower(expression);
					attributeValue = attributeValue.toString().toLowerCase();
				}

				switch (example.getStringMatcherForPath(currentPath)) {

					case DEFAULT:
					case EXACT:
						predicates.add(cb.equal(expression, attributeValue));
						break;
					case CONTAINING:
						predicates.add(cb.like(expression, "%" + attributeValue + "%"));
						break;
					case STARTING:
						predicates.add(cb.like(expression, attributeValue + "%"));
						break;
					case ENDING:
						predicates.add(cb.like(expression, "%" + attributeValue));
						break;
					default:
						throw new IllegalArgumentException("Unsupported StringMatcher "
								+ example.getStringMatcherForPath(currentPath));
				}
			} else {
				predicates.add(cb.equal(from.get(attribute), attributeValue));
			}
		}

		return predicates;
	}

	private static boolean isAssociation(Attribute<?, ?> attribute) {
		return ASSOCIATION_TYPES.contains(attribute.getPersistentAttributeType());
	}

	/**
	 * {@link PathNode} is used to dynamically grow a directed graph structure that allows to detect cycles within its
	 * direct predecessor nodes by comparing parent node values using {@link System#identityHashCode(Object)}.
	 * 
	 * @author Christoph Strobl
	 */
	private static class PathNode {

		String name;
		PathNode parent;
		List<PathNode> siblings = new ArrayList<PathNode>();;
		Object value;

		public PathNode(String edge, PathNode parent, Object value) {

			this.name = edge;
			this.parent = parent;
			this.value = value;
		}

		PathNode add(String attribute, Object value) {

			PathNode node = new PathNode(attribute, this, value);
			siblings.add(node);
			return node;
		}

		boolean spansCycle() {

			if (value == null) {
				return false;
			}

			String identityHex = ObjectUtils.getIdentityHexString(value);
			PathNode tmp = parent;

			while (tmp != null) {

				if (ObjectUtils.getIdentityHexString(tmp.value).equals(identityHex)) {
					return true;
				}
				tmp = tmp.parent;
			}

			return false;
		}

		@Override
		public String toString() {

			StringBuilder sb = new StringBuilder();
			if (parent != null) {
				sb.append(parent.toString());
				sb.append(" -");
				sb.append(name);
				sb.append("-> ");
			}

			sb.append("[{ ");
			sb.append(ObjectUtils.nullSafeToString(value));
			sb.append(" }]");
			return sb.toString();
		}
	}
}
