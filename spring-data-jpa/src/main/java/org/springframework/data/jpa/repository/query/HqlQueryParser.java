/*
 * Copyright 2022-2023 the original author or authors.
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

import java.util.List;

import org.antlr.v4.runtime.ParserRuleContext;
import org.springframework.data.domain.Sort;
import org.springframework.lang.Nullable;

/**
 * Implements the various parsing operations using {@link HqlQueryTransformer} as well as {@link HqlUtils}.
 * 
 * @author Greg Turnquist
 * @since 3.1
 */
class HqlQueryParser implements QueryParser {

	private final DeclaredQuery query;

	@Nullable private Sort sort;

	HqlQueryParser(DeclaredQuery query, @Nullable Sort sort) {

		this.query = query;
		this.sort = sort;
	}

	HqlQueryParser(String query, @Nullable Sort sort) {
		this(DeclaredQuery.of(query, false), sort);
	}

	HqlQueryParser(DeclaredQuery query) {
		this(query, null);
	}

	HqlQueryParser(String query) {
		this(DeclaredQuery.of(query, false), null);
	}

	@Override
	public DeclaredQuery getDeclaredQuery() {
		return query;
	}

	@Override
	public ParserRuleContext parse() {
		return HqlUtils.parseWithFastFailure(getQuery());
	}

	@Override
	public List<QueryParsingToken> applySorting(ParserRuleContext parsedQuery) {
		return new HqlQueryTransformer(sort).visit(parsedQuery);
	}

	@Override
	public List<QueryParsingToken> count(ParserRuleContext parsedQuery) {
		return new HqlQueryTransformer(true).visit(parsedQuery);
	}

	@Override
	public String findAlias(ParserRuleContext parsedQuery) {

		HqlQueryTransformer transformVisitor = new HqlQueryTransformer();
		transformVisitor.visit(parsedQuery);
		return transformVisitor.getAlias();
	}

	@Override
	public List<QueryParsingToken> projection(ParserRuleContext parsedQuery) {

		HqlQueryTransformer transformVisitor = new HqlQueryTransformer();
		transformVisitor.visit(parsedQuery);
		return transformVisitor.getProjection();
	}

	@Override
	public boolean hasConstructor(ParserRuleContext parsedQuery) {

		HqlQueryTransformer transformVisitor = new HqlQueryTransformer();
		transformVisitor.visit(parsedQuery);
		return transformVisitor.hasConstructorExpression();
	}

	@Override
	public void setSort(Sort sort) {
		this.sort = sort;
	}
}
