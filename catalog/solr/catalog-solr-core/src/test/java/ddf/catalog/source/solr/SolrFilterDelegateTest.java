/**
 * Copyright (c) Codice Foundation
 *
 * <p>This is free software: you can redistribute it and/or modify it under the terms of the GNU
 * Lesser General Public License as published by the Free Software Foundation, either version 3 of
 * the License, or any later version.
 *
 * <p>This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details. A copy of the GNU Lesser General Public
 * License is distributed along with this program and can be found at
 * <http://www.gnu.org/licenses/lgpl.html>.
 */
package ddf.catalog.source.solr;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.startsWith;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ddf.catalog.data.AttributeType.AttributeFormat;
import ddf.catalog.data.Metacard;
import ddf.catalog.data.types.Core;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.TimeZone;
import org.apache.solr.client.solrj.SolrQuery;
import org.junit.Test;
import org.locationtech.jts.geom.MultiPolygon;
import org.locationtech.jts.io.ParseException;
import org.locationtech.jts.io.WKTReader;
import org.locationtech.jts.operation.union.UnaryUnionOp;

public class SolrFilterDelegateTest {

  private static final String TOKENIZED_METADATA_FIELD =
      Metacard.METADATA + SchemaFields.TEXT_SUFFIX + SchemaFields.TOKENIZED;

  private DynamicSchemaResolver mockResolver = mock(DynamicSchemaResolver.class);

  private SolrFilterDelegate toTest = new SolrFilterDelegate(mockResolver, Collections.emptyMap());

  @Test(expected = UnsupportedOperationException.class)
  public void intersectsWithNullWkt() {
    // given null WKT and a valid property name
    when(mockResolver.getField(
            "testProperty", AttributeFormat.GEOMETRY, false, Collections.emptyMap()))
        .thenReturn("testProperty_geohash_index");
    // when the delegate intersects
    toTest.intersects("testProperty", null);
    // then the operation is unsupported
  }

  @Test(expected = UnsupportedOperationException.class)
  public void intersectsWithNullPropertyName() {
    // given null property name
    // when the delegate intersects
    toTest.intersects(null, "wkt");
    // then the operation is unsupported
  }

  @Test
  public void intersectsWithInvalidJtsWkt() {
    // given a geospatial property
    when(mockResolver.getField(
            "testProperty", AttributeFormat.GEOMETRY, false, Collections.emptyMap()))
        .thenReturn("testProperty_geohash_index");

    // when the delegate intersects on WKT not handled by JTS
    SolrQuery query = toTest.intersects("testProperty", "invalid JTS wkt");

    // then return a valid Solr query using the given WKT
    assertThat(query.getQuery(), is("testProperty_geohash_index:\"Intersects(invalid JTS wkt)\""));
  }

  @Test
  public void intersectsWithValidJtsWkt() {
    // given a geospatial property
    when(mockResolver.getField(
            "testProperty", AttributeFormat.GEOMETRY, false, Collections.emptyMap()))
        .thenReturn("testProperty_geohash_index");

    // when the delegate intersects on WKT not handled by JTS
    SolrQuery query = toTest.intersects("testProperty", "POINT(1 0)");

    // then return a valid Solr query using the given WKT
    assertThat(
        query.getQuery(),
        startsWith("testProperty_geohash_index:\"Intersects(BUFFER(POINT(1.0 0.0), "));
  }

  @Test
  public void selfIntersectingPolygon() {
    String wkt = "POLYGON((0 0, 10 0, 10 20, 5 -5, 0 20, 0 0))";
    when(mockResolver.getField(
            "testProperty", AttributeFormat.GEOMETRY, false, Collections.emptyMap()))
        .thenReturn("testProperty_geohash_index");
    SolrQuery query = toTest.contains("testProperty", wkt);
    assertThat(
        query.getQuery(),
        startsWith(
            "testProperty_geohash_index:\"Contains(POLYGON ((5 -5, 0 0, 0 20, 10 20, 10 0, 5 -5)))\""));
  }

  @Test
  public void squarePolygon() {
    String wkt = "POLYGON ((0 10, 0 30, 20 30, 20 10, 0 10))";
    when(mockResolver.getField(
            "testProperty", AttributeFormat.GEOMETRY, false, Collections.emptyMap()))
        .thenReturn("testProperty_geohash_index");
    SolrQuery query = toTest.contains("testProperty", wkt);
    assertThat(
        query.getQuery(),
        startsWith(
            "testProperty_geohash_index:\"Contains(POLYGON ((0 10, 0 30, 20 30, 20 10, 0 10)))\""));
  }

  @Test
  public void nonIntersectingPolygon() {
    String wkt = "POLYGON((5 -5, 0 0, 0 20, 10 20, 10 0, 5 -5))";
    when(mockResolver.getField(
            "testProperty", AttributeFormat.GEOMETRY, false, Collections.emptyMap()))
        .thenReturn("testProperty_geohash_index");
    SolrQuery query = toTest.contains("testProperty", wkt);
    assertThat(
        query.getQuery(),
        startsWith(
            "testProperty_geohash_index:\"Contains(POLYGON ((5 -5, 0 0, 0 20, 10 20, 10 0, 5 -5)))\""));
  }

  @Test
  public void polygonWithHoleAndSelfIntersecting() {
    // in the case of a self-intersecting polygon with a hole the hole is lost in the conversion
    String wkt = "POLYGON ((0 0, 0 10, 13 3, 13 9, 0 0), (1 4, 1 7, 3 6, 3 4, 1 4))";
    when(mockResolver.getField(
            "testProperty", AttributeFormat.GEOMETRY, false, Collections.emptyMap()))
        .thenReturn("testProperty_geohash_index");
    SolrQuery query = toTest.contains("testProperty", wkt);
    assertThat(
        query.getQuery(),
        startsWith(
            "testProperty_geohash_index:\"Contains(POLYGON ((0 0, 0 10, 13 9, 13 3, 0 0)))\""));
  }

  @Test
  public void multiPolygon() throws ParseException {
    String wkt =
        "MULTIPOLYGON (((30 20, 45 40, 10 40, 30 20)), ((15 5, 40 10, 10 20, 5 10, 15 5)))";
    when(mockResolver.getField(
            "testProperty", AttributeFormat.GEOMETRY, false, Collections.emptyMap()))
        .thenReturn("testProperty_geohash_index");
    SolrQuery query = toTest.contains("testProperty", wkt);
    MultiPolygon multiPolygon = (MultiPolygon) new WKTReader().read(wkt);
    // allowMultiOverlap is enabled, so spatial4j will calculate the union of any MultiPolygon.
    // This means we need to calculate the union of the expected WKT for the assertion.
    MultiPolygon unioned = (MultiPolygon) new UnaryUnionOp(multiPolygon).union();
    assertThat(
        query.getQuery(),
        startsWith(String.format("testProperty_geohash_index:\"Contains(%s)\"", unioned.toText())));
  }

  @Test
  public void polygonWithHole() {
    String wkt = "POLYGON ((35 10, 45 45, 15 40, 10 20, 35 10), (20 30, 35 35, 30 20, 20 30))";
    when(mockResolver.getField(
            "testProperty", AttributeFormat.GEOMETRY, false, Collections.emptyMap()))
        .thenReturn("testProperty_geohash_index");
    SolrQuery query = toTest.contains("testProperty", wkt);
    assertThat(
        query.getQuery(),
        startsWith(
            "testProperty_geohash_index:\"Contains(POLYGON ((35 10, 45 45, 15 40, 10 20, 35 10), (20 30, 35 35, 30 20, 20 30)))\""));
  }

  @Test
  public void bufferedPolygonHolesRemovedIfCrossingDateline() {
    String wkt =
        "POLYGON ((170 10, -170 10, -170 0, 170 0, 170 10), (171 9, 172 9, 172 8, 172 8, 171 9))";
    when(mockResolver.getField(
            "testProperty", AttributeFormat.GEOMETRY, false, Collections.emptyMap()))
        .thenReturn("testProperty_geohash_index");
    // buffer of 0 so the final WKT is easy to calculate
    SolrQuery query = toTest.dwithin("testProperty", wkt, 0);
    assertThat(
        query.getQuery(),
        startsWith(
            "testProperty_geohash_index:\"Intersects(MULTIPOLYGON (((-180 0, -180 10, -170 10, -170 0, -180 0)), ((180 10, 180 0, 170 0, 170 10, 180 10))))\""));
  }

  @Test
  public void bufferedMultiPolygonHolesRemovedIfCrossingDateline() {
    String wkt =
        "MULTIPOLYGON (((170 10, -170 10, -170 0, 170 0, 170 10), (171 9, 172 9, 172 8, 172 8, 171 9)), ((170 30, -170 30, -170 20, 170 20, 170 30), (171 29, 172 29, 172 28, 172 28, 171 29)))";
    when(mockResolver.getField(
            "testProperty", AttributeFormat.GEOMETRY, false, Collections.emptyMap()))
        .thenReturn("testProperty_geohash_index");
    // buffer of 0 so the final WKT is easy to calculate
    SolrQuery query = toTest.dwithin("testProperty", wkt, 0);
    assertThat(
        query.getQuery(),
        startsWith(
            "testProperty_geohash_index:\"Intersects(MULTIPOLYGON (((-180 0, -180 10, -170 10, -170 0, -180 0)), ((180 10, 180 0, 170 0, 170 10, 180 10)), ((-180 20, -180 30, -170 30, -170 20, -180 20)), ((180 30, 180 20, 170 20, 170 30, 180 30))))\""));
  }

  @Test
  public void reservedSpecialCharactersIsEqual() {
    // given a text property
    when(mockResolver.getField(
            "testProperty", AttributeFormat.STRING, true, Collections.emptyMap()))
        .thenReturn("testProperty_txt_index");

    // when searching for exact reserved characters
    SolrQuery equalQuery =
        toTest.propertyIsEqualTo("testProperty", "+ - && || ! ( ) { } [ ] ^ \" ~ :", true);

    // then return escaped special characters in the query
    assertThat(
        equalQuery.getQuery(),
        is(
            "testProperty_txt_index:\"\\+ \\- \\&& \\|| \\! \\( \\) \\{ \\} \\[ \\] \\^ \\\" \\~ \\:\""));
  }

  @Test
  public void reservedSpecialCharactersIsLike() {
    // given a tokenized text property
    when(mockResolver.getField(
            "testProperty", AttributeFormat.STRING, true, Collections.emptyMap()))
        .thenReturn("testProperty_txt");
    when(mockResolver.getSpecialIndexSuffix(AttributeFormat.STRING, Collections.emptyMap()))
        .thenReturn(SchemaFields.TOKENIZED);
    when(mockResolver.getCaseSensitiveField("testProperty_txt_tokenized", Collections.emptyMap()))
        .thenReturn("testProperty_txt_tokenized_tokenized");

    // when searching for like reserved characters
    SolrQuery likeQuery =
        toTest.propertyIsLike("testProperty", "+ - && || ! ( ) { } [ ] ^ \" ~ : \\*?", true);

    // then return escaped special characters in the query
    assertThat(
        likeQuery.getQuery(),
        is(
            "(testProperty_txt_tokenized_tokenized:(\\+ \\- \\&& \\|| \\! \\( \\) \\{ \\} \\[ \\] \\^ \\\" \\~ \\: \\*?))"));
  }

  /*
    DDF-314: COmmented out until the ANY_TEXT functionality is added back
    in - then these tests can be activated.

  @Test
  public void testPropertyIsEqualTo_AnyText_CaseSensitive() {
      String expectedQuery = "any_text:\"mySearchPhrase\"";
      String searchPhrase = "mySearchPhrase";
      boolean isCaseSensitive = true;
      SolrQuery equalToQuery = toTest.propertyIsEqualTo(Metacard.ANY_TEXT, searchPhrase,
              isCaseSensitive);
      assertThat(equalToQuery.getQuery(), is(expectedQuery));
  }

  @Test(expected = UnsupportedOperationException.class)
  public void testPropertyIsEqualTo_AnyText_CaseInsensitive() {
      String searchPhrase = "mySearchPhrase";
      boolean isCaseSensitive = false;
      toTest.propertyIsEqualTo(Metacard.ANY_TEXT, searchPhrase, isCaseSensitive);
  }

  @Test
  public void testPropertyIsFuzzy_AnyText() {
      String expectedQuery = "+any_text:mysearchphrase~ ";
      String searchPhrase = "mySearchPhrase";
      SolrQuery fuzzyQuery = toTest.propertyIsFuzzy(Metacard.ANY_TEXT, searchPhrase);
      assertThat(fuzzyQuery.getQuery(), is(expectedQuery));
  }

  @Test
  public void testPropertyIsLike_AnyText_CaseInsensitive() {
      String expectedQuery = "any_text:\"mySearchPhrase\"";
      String searchPhrase = "mySearchPhrase";
      boolean isCaseSensitive = false;
      SolrQuery isLikeQuery = toTest.propertyIsLike(Metacard.ANY_TEXT, searchPhrase,
              isCaseSensitive);
      assertThat(isLikeQuery.getQuery(), is(expectedQuery));
  }

  @Test
  public void testPropertyIsLike_AnyText_CaseSensitive() {
      String expectedQuery = "any_text_has_case:\"mySearchPhrase\"";
      String searchPhrase = "mySearchPhrase";
      boolean isCaseSensitive = true;
      when(mockResolver.getCaseSensitiveField("any_text")).thenReturn(
              "any_text" + ddf.catalog.source.solr.SchemaFields.HAS_CASE);
      SolrQuery isLikeQuery = toTest.propertyIsLike(Metacard.ANY_TEXT, searchPhrase,
              isCaseSensitive);
      assertThat(isLikeQuery.getQuery(), is(expectedQuery));
  }
  END OMIT per DDF-314*/

  @Test
  public void testPropertyIsEqualToEmpty() {
    when(mockResolver.getField("title", AttributeFormat.STRING, true, Collections.emptyMap()))
        .thenReturn("title_txt");

    String searchPhrase = "";
    String expectedQuery = "-title_txt:[\"\" TO *]";
    boolean isCaseSensitive = true;

    SolrQuery isEqualTo = toTest.propertyIsEqualTo("title", searchPhrase, isCaseSensitive);

    assertThat(isEqualTo.getQuery(), is(expectedQuery));
  }

  @Test
  public void testPropertyIsEqualToInteger() {
    when(mockResolver.getField("anumber", AttributeFormat.INTEGER, true, Collections.emptyMap()))
        .thenReturn("anumber_int");

    int searchPhrase = 1;
    String expectedQuery = "anumber_int:1";

    SolrQuery isEqualTo = toTest.propertyIsEqualTo("anumber", searchPhrase);

    assertThat(isEqualTo.getQuery().trim(), is(expectedQuery));
  }

  @Test
  public void testPropertyIsEqualToShort() {
    when(mockResolver.getField("anumber", AttributeFormat.SHORT, true, Collections.emptyMap()))
        .thenReturn("anumber_shr");

    short searchPhrase = 1;
    String expectedQuery = "anumber_shr:1";

    SolrQuery isEqualTo = toTest.propertyIsEqualTo("anumber", searchPhrase);

    assertThat(isEqualTo.getQuery().trim(), is(expectedQuery));
  }

  @Test
  public void testPropertyIsEqualToLong() {
    when(mockResolver.getField("anumber", AttributeFormat.LONG, true, Collections.emptyMap()))
        .thenReturn("anumber_lng");

    long searchPhrase = 1;
    String expectedQuery = "anumber_lng:1";

    SolrQuery isEqualTo = toTest.propertyIsEqualTo("anumber", searchPhrase);

    assertThat(isEqualTo.getQuery().trim(), is(expectedQuery));
  }

  @Test
  public void testPropertyIsEqualToFloat() {
    when(mockResolver.getField("anumber", AttributeFormat.FLOAT, true, Collections.emptyMap()))
        .thenReturn("anumber_flt");

    float searchPhrase = 1;
    String expectedQuery = "anumber_flt:1.0";

    SolrQuery isEqualTo = toTest.propertyIsEqualTo("anumber", searchPhrase);

    assertThat(isEqualTo.getQuery().trim(), is(expectedQuery));
  }

  @Test
  public void testPropertyIsEqualToNegativeFloat() {
    when(mockResolver.getField("anumber", AttributeFormat.FLOAT, true, Collections.emptyMap()))
        .thenReturn("anumber_flt");

    float searchPhrase = -1;
    String expectedQuery = "anumber_flt:\\-1.0";

    SolrQuery isEqualTo = toTest.propertyIsEqualTo("anumber", searchPhrase);

    assertThat(isEqualTo.getQuery().trim(), is(expectedQuery));
  }

  @Test
  public void testPropertyIsEqualToDouble() {
    when(mockResolver.getField("anumber", AttributeFormat.FLOAT, true, Collections.emptyMap()))
        .thenReturn("anumber_dbl");

    double searchPhrase = 1;
    String expectedQuery = "anumber_dbl:1.0";

    SolrQuery isEqualTo = toTest.propertyIsEqualTo("anumber", searchPhrase);

    assertThat(isEqualTo.getQuery().trim(), is(expectedQuery));
  }

  @Test
  public void testPropertyIsEqualToNegativeInteger() {
    when(mockResolver.getField("anumber", AttributeFormat.INTEGER, true, Collections.emptyMap()))
        .thenReturn("anumber_int");

    int searchPhrase = -1;
    String expectedQuery = "anumber_int:\\-1";

    SolrQuery isEqualTo = toTest.propertyIsEqualTo("anumber", searchPhrase);

    assertThat(isEqualTo.getQuery().trim(), is(expectedQuery));
  }

  @Test
  public void testPropertyIsEqualToBoolean() {
    when(mockResolver.getField("aboolean", AttributeFormat.BOOLEAN, true, Collections.emptyMap()))
        .thenReturn("aboolean_bln");

    boolean searchPhrase = true;
    String expectedQuery = "aboolean_bln:true";

    SolrQuery isEqualTo = toTest.propertyIsEqualTo("aboolean", searchPhrase);

    assertThat(isEqualTo.getQuery().trim(), is(expectedQuery));
  }

  @Test(expected = UnsupportedOperationException.class)
  public void testPropertyIsEqualToNull() {
    when(mockResolver.getField("title", AttributeFormat.STRING, true, Collections.emptyMap()))
        .thenReturn("title_txt");

    String searchPhrase = null;
    boolean isCaseSensitive = true;

    toTest.propertyIsEqualTo("title", searchPhrase, isCaseSensitive);
  }

  @Test
  public void testPropertyIsLikeWildcard() {
    when(mockResolver.anyTextFields())
        .thenReturn(Collections.singletonList("metadata_txt").stream());

    String searchPhrase = "*";
    String expectedQuery = "*:*";
    boolean isCaseSensitive = false;

    SolrQuery isLikeQuery = toTest.propertyIsLike(Metacard.ANY_TEXT, searchPhrase, isCaseSensitive);

    assertThat(isLikeQuery.getQuery(), is(expectedQuery));
  }

  @Test
  public void testPropertyIsLikeTermAndWildcard() {
    when(mockResolver.anyTextFields())
        .thenReturn(Collections.singletonList("metadata_txt").stream());
    when(mockResolver.getSpecialIndexSuffix(AttributeFormat.STRING, Collections.emptyMap()))
        .thenReturn(SchemaFields.TOKENIZED);

    String searchPhrase = "abc-123*";
    String expectedQuery = "(" + TOKENIZED_METADATA_FIELD + ":(abc\\-123*))";
    boolean isCaseSensitive = false;

    SolrQuery isLikeQuery = toTest.propertyIsLike(Metacard.ANY_TEXT, searchPhrase, isCaseSensitive);

    assertThat(isLikeQuery.getQuery(), is(expectedQuery));
  }

  @Test
  public void testPropertyIsLikeEmpty() {
    when(mockResolver.getField("title", AttributeFormat.STRING, false, Collections.emptyMap()))
        .thenReturn("title_txt");

    String searchPhrase = "";
    String expectedQuery = "-title_txt:[\"\" TO *]";
    boolean isCaseSensitive = false;

    SolrQuery isLikeQuery = toTest.propertyIsLike("title", searchPhrase, isCaseSensitive);

    assertThat(isLikeQuery.getQuery(), is(expectedQuery));
  }

  @Test(expected = UnsupportedOperationException.class)
  public void testPropertyIsNull() {
    when(mockResolver.getField("title", AttributeFormat.STRING, false, Collections.emptyMap()))
        .thenReturn("title_txt");

    String searchPhrase = null;
    boolean isCaseSensitive = false;

    toTest.propertyIsLike("title", searchPhrase, isCaseSensitive);
  }

  @Test
  public void testPropertyIsLikeWildcardNoTokens() {
    when(mockResolver.anyTextFields())
        .thenReturn(Collections.singletonList("metadata_txt").stream());
    when(mockResolver.getSpecialIndexSuffix(AttributeFormat.STRING, Collections.emptyMap()))
        .thenReturn(SchemaFields.TOKENIZED);

    String searchPhrase = "title*";
    String expectedQuery = "(" + TOKENIZED_METADATA_FIELD + ":(title*))";
    boolean isCaseSensitive = false;

    SolrQuery isLikeQuery = toTest.propertyIsLike(Metacard.ANY_TEXT, searchPhrase, isCaseSensitive);

    assertThat(isLikeQuery.getQuery(), is(expectedQuery));
  }

  @Test
  public void testPropertyIsLikeMultipleTermsWithWildcard() {
    when(mockResolver.anyTextFields())
        .thenReturn(Collections.singletonList("metadata_txt").stream());
    when(mockResolver.getSpecialIndexSuffix(AttributeFormat.STRING, Collections.emptyMap()))
        .thenReturn(SchemaFields.TOKENIZED);

    String searchPhrase = "abc 123*";
    String expectedQuery = "(" + TOKENIZED_METADATA_FIELD + ":(abc 123*))";

    SolrQuery isLikeQuery = toTest.propertyIsLike(Metacard.ANY_TEXT, searchPhrase, false);

    assertThat(isLikeQuery.getQuery(), is(expectedQuery));
  }

  @Test
  public void testPropertyIsLikeCaseSensitiveWildcard() {
    when(mockResolver.anyTextFields())
        .thenReturn(Collections.singletonList("metadata_txt").stream());
    when(mockResolver.getSpecialIndexSuffix(AttributeFormat.STRING, Collections.emptyMap()))
        .thenReturn(SchemaFields.TOKENIZED);
    when(mockResolver.getCaseSensitiveField("metadata_txt_tokenized", Collections.emptyMap()))
        .thenReturn("metadata_txt_tokenized_has_case");

    String searchPhrase = "abc-123*";
    String expectedQuery =
        "(" + TOKENIZED_METADATA_FIELD + SchemaFields.HAS_CASE + ":(abc\\-123*))";

    SolrQuery isLikeQuery = toTest.propertyIsLike(Metacard.ANY_TEXT, searchPhrase, true);

    assertThat(isLikeQuery.getQuery(), is(expectedQuery));
  }

  @Test
  public void testTemporalBefore() {
    when(mockResolver.getField("created", AttributeFormat.DATE, false, Collections.emptyMap()))
        .thenReturn("created_date");

    String expectedQuery = " created_date:[ * TO 1995-11-24T23:59:56.765Z } ";
    SolrQuery temporalQuery = toTest.before(Metacard.CREATED, getCannedTime());
    assertThat(temporalQuery.getQuery(), is(expectedQuery));
  }

  @Test
  public void testTemporalAfter() {
    when(mockResolver.getField("created", AttributeFormat.DATE, false, Collections.emptyMap()))
        .thenReturn("created_date");

    String expectedQuery = " created_date:{ 1995-11-24T23:59:56.765Z TO * ] ";
    SolrQuery temporalQuery = toTest.after(Metacard.CREATED, getCannedTime());
    assertThat(temporalQuery.getQuery(), is(expectedQuery));
  }

  @Test
  public void testDatePropertyGreaterThan() {
    when(mockResolver.getField("created", AttributeFormat.DATE, false, Collections.emptyMap()))
        .thenReturn("created_date");

    String expectedQuery = " created_date:{ 1995-11-24T23:59:56.765Z TO * ] ";
    SolrQuery temporalQuery = toTest.propertyIsGreaterThan(Metacard.CREATED, getCannedTime());
    assertThat(temporalQuery.getQuery(), is(expectedQuery));
  }

  @Test
  public void testDatePropertyGreaterThanOrEqualTo() {
    when(mockResolver.getField("created", AttributeFormat.DATE, false, Collections.emptyMap()))
        .thenReturn("created_date");

    String expectedQuery = " created_date:[ 1995-11-24T23:59:56.765Z TO * ] ";
    SolrQuery temporalQuery =
        toTest.propertyIsGreaterThanOrEqualTo(Metacard.CREATED, getCannedTime());
    assertThat(temporalQuery.getQuery(), is(expectedQuery));
  }

  @Test
  public void testDatePropertyLessThan() {
    when(mockResolver.getField("created", AttributeFormat.DATE, false, Collections.emptyMap()))
        .thenReturn("created_date");

    String expectedQuery = " created_date:[ * TO 1995-11-24T23:59:56.765Z } ";
    SolrQuery temporalQuery = toTest.propertyIsLessThan(Metacard.CREATED, getCannedTime());
    assertThat(temporalQuery.getQuery(), is(expectedQuery));
  }

  @Test
  public void testDatePropertyLessThanOrEqualTo() {
    when(mockResolver.getField("created", AttributeFormat.DATE, false, Collections.emptyMap()))
        .thenReturn("created_date");

    String expectedQuery = " created_date:[ * TO 1995-11-24T23:59:56.765Z ] ";
    SolrQuery temporalQuery = toTest.propertyIsLessThanOrEqualTo(Metacard.CREATED, getCannedTime());
    assertThat(temporalQuery.getQuery(), is(expectedQuery));
  }

  @Test
  public void testDatePropertyIsBetween() {
    when(mockResolver.getField("created", AttributeFormat.DATE, false, Collections.emptyMap()))
        .thenReturn("created_date");

    String expectedQuery =
        " created_date:[ 1995-11-24T23:59:56.765Z TO 1995-11-27T04:59:56.765Z ] ";
    SolrQuery temporalQuery =
        toTest.propertyIsBetween(
            Metacard.CREATED, getCannedTime(), getCannedTime(1995, Calendar.NOVEMBER, 27, 4));
    assertThat(temporalQuery.getQuery(), is(expectedQuery));
  }

  @Test
  public void testNumberIsBetweenLongs() {
    when(mockResolver.getField("altitude", AttributeFormat.LONG, true, Collections.emptyMap()))
        .thenReturn("altitude");

    String expectedQuery = " altitude:[ -100 TO 100] ";
    Number lowerBoundary = -100;
    Number upperBoundary = 100;
    SolrQuery numberQuery =
        toTest.propertyIsBetween("altitude", lowerBoundary.longValue(), upperBoundary.longValue());
    assertThat(numberQuery.getQuery(), is(expectedQuery));
  }

  @Test
  public void testNumberIsBetweenFloats() {

    when(mockResolver.getField("altitude", AttributeFormat.FLOAT, true, Collections.emptyMap()))
        .thenReturn("altitude");

    String expectedQuery = " altitude:[ -100.3 TO 0.4] ";
    Number lowerBoundary = -100.3;
    Number upperBoundary = 0.4;
    SolrQuery numberQuery =
        toTest.propertyIsBetween(
            "altitude", lowerBoundary.floatValue(), upperBoundary.floatValue());
    assertThat(numberQuery.getQuery(), is(expectedQuery));
  }

  @Test
  public void testNumberIsBetweenInts() {

    when(mockResolver.getField("altitude", AttributeFormat.INTEGER, true, Collections.emptyMap()))
        .thenReturn("altitude");

    String expectedQuery = " altitude:[ -100 TO 0] ";
    Number lowerBoundary = -100;
    Number upperBoundary = 0;
    SolrQuery numberQuery =
        toTest.propertyIsBetween("altitude", lowerBoundary.intValue(), upperBoundary.intValue());
    assertThat(numberQuery.getQuery(), is(expectedQuery));
  }

  @Test
  public void testNumberIsBetweenShorts() {

    when(mockResolver.getField("altitude", AttributeFormat.SHORT, true, Collections.emptyMap()))
        .thenReturn("altitude");

    String expectedQuery = " altitude:[ 0 TO 50] ";
    Number lowerBoundary = 0;
    Number upperBoundary = 50;
    SolrQuery numberQuery =
        toTest.propertyIsBetween(
            "altitude", lowerBoundary.shortValue(), upperBoundary.shortValue());
    assertThat(numberQuery.getQuery(), is(expectedQuery));
  }

  @Test
  public void testNumberIsBetweenFloatAndInt() {

    when(mockResolver.getField("altitude", AttributeFormat.FLOAT, true, Collections.emptyMap()))
        .thenReturn("altitude");

    String expectedQuery = " altitude:[ -100.567 TO 0.0] ";
    Number lowerBoundary = -100.567;
    Number upperBoundary = 0;
    SolrQuery numberQuery =
        toTest.propertyIsBetween("altitude", lowerBoundary.floatValue(), upperBoundary.intValue());
    assertThat(numberQuery.getQuery(), is(expectedQuery));
  }

  @Test(expected = java.lang.IllegalArgumentException.class)
  public void testBetweenCastException() {

    String nonNumber = "Not A Number";
    SolrQuery notANumberQuery =
        toTest.propertyIsBetween("altitude", (Object) nonNumber, (Object) nonNumber);

    notANumberQuery.getQuery();
  }

  @Test
  public void testPropertyIsInProximityTo() {
    when(mockResolver.getField("title", AttributeFormat.STRING, true, Collections.emptyMap()))
        .thenReturn("title_txt");
    when(mockResolver.getSpecialIndexSuffix(AttributeFormat.STRING, Collections.emptyMap()))
        .thenReturn(SchemaFields.TOKENIZED);

    String expectedQuery = "(title_txt_tokenized:\"a proximity string\" ~2)";
    SolrQuery solrQuery = toTest.propertyIsInProximityTo(Core.TITLE, 2, "a proximity string");

    assertThat(solrQuery.getQuery(), is(expectedQuery));
  }

  @Test
  public void testPropertyIsDivisibleBy() {
    when(mockResolver.getAnonymousField(Core.RESOURCE_SIZE))
        .thenReturn(Collections.singletonList("resource-size_lng"));

    long divisibleBy = 2L;
    String expectedQuery = "_val_:\"{!frange l=0 u=0}mod(field(resource-size_lng,min),2)\"";

    SolrQuery isLikeQuery = toTest.propertyIsDivisibleBy(Core.RESOURCE_SIZE, divisibleBy);

    assertThat(isLikeQuery.getQuery(), is(expectedQuery));
  }

  private Date getCannedTime() {
    return getCannedTime(1995, Calendar.NOVEMBER, 24, 23);
  }

  private Date getCannedTime(int year, int month, int day, int hour) {
    Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
    calendar.clear();
    calendar.set(year, month, day, hour, 59, 56);
    calendar.set(Calendar.MILLISECOND, 765);
    return calendar.getTime();
  }
}
