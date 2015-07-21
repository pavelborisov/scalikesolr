/*
 * Copyright 2011 Kazuhiro Sera
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language
 * governing permissions and limitations under the License.
 */

package com.github.seratch.scalikesolr.response.query

import scala.beans.BeanProperty
import scala.collection.immutable.ListMap
import com.github.seratch.scalikesolr.request.common.WriterType
import org.apache.solr.common.util.NamedList
import scala.xml.{ Node, XML }
import com.github.seratch.scalikesolr.{ SolrDocumentValue, SolrDocumentBinValue, SolrDocument }

case class Facet(@BeanProperty val facetQueries: Map[String, SolrDocument],
    @BeanProperty val facetFields: Map[String, SolrDocument],
    @deprecated(message = """
   NOTE: as of Solr3.1 Date Faceting has been deprecated
   in favor of the more general Range Faceting described below.
   The response structure is slightly differnet, but the funtionality is equivilent
   (except that it supports numeric fields as well as dates)
                                       """, since = "Solr 3.1")@BeanProperty val facetDates: Map[String, SolrDocument],
    @BeanProperty val facetRanges: Map[String, SolrDocument]) {

  def getFromFacetFields(name: String): SolrDocument = facetFields.getOrElse(name, null)

  @deprecated(message = """
   NOTE: as of Solr3.1 Date Faceting has been deprecated
   in favor of the more general Range Faceting described below.
   The response structure is slightly differnet, but the funtionality is equivilent
   (except that it supports numeric fields as well as dates)
                        """, since = "Solr 3.1") def getFromFacetDates(date: String): SolrDocument = facetDates.getOrElse(date, null)

  def getFromFacetRanges(range: String): SolrDocument = facetRanges.getOrElse(range, null)

}

object Facet {

  def extract(writerType: WriterType = WriterType.Standard,
    rawBody: String = "",
    rawJavabin: NamedList[Any] = null): Facet = {

    writerType match {
      case WriterType.Standard => {

        val facetQueriesMap = new collection.mutable.LinkedHashMap[String, SolrDocument]
        val facetFieldsMap = new collection.mutable.LinkedHashMap[String, SolrDocument]
        val facetDatesMap = new collection.mutable.LinkedHashMap[String, SolrDocument]
        val facetRangesMap = new collection.mutable.LinkedHashMap[String, SolrDocument]

        val xml = XML.loadString(rawBody)
        val facetCountsList = (xml \ "lst").filter(lst => (lst \ "@name").text == "facet_counts")
        facetCountsList.size match {
          case 0 =>
          case _ =>
            facetCountsList.head.child foreach {
              case node: Node =>
                (node \ "@name").text match {
                  case "facet_queries" =>
                    node.child foreach {
                      query =>
                        val results = query.child map (value => ((value \ "@name").text, SolrDocumentValue(value.text)))
                        facetQueriesMap.update((query \ "@name").text, SolrDocument(map = ListMap.empty[String, SolrDocumentValue] ++ results))
                    }
                  case "facet_fields" =>
                    node.child foreach {
                      field =>
                        val results = field.child map (value => ((value \ "@name").text, SolrDocumentValue(value.text)))
                        facetFieldsMap.update((field \ "@name").text, SolrDocument(map = ListMap.empty[String, SolrDocumentValue] ++ results))
                    }
                  case "facet_dates" =>
                    (node \ "lst") foreach {
                      date =>
                        val results = date.child map (value => ((value \ "@name").text, SolrDocumentValue(value.text)))
                        facetDatesMap.update((date \ "@name").text, SolrDocument(map = ListMap.empty[String, SolrDocumentValue] ++ results))
                    }
                  case "facet_ranges" =>
                    (node \ "lst") foreach {
                      range =>
                        val results = range.child map (value => ((value \ "@name").text, SolrDocumentValue(value.text)))
                        facetRangesMap.update((range \ "@name").text, SolrDocument(map = ListMap.empty[String, SolrDocumentValue] ++ results))
                    }
                  case _ =>
                }
              case _ =>
            }
        }
        new Facet(
          facetQueries = facetQueriesMap.toMap,
          facetFields = facetFieldsMap.toMap,
          facetDates = facetDatesMap.toMap,
          facetRanges = facetRangesMap.toMap
        )
      }

      case WriterType.JavaBinary =>

        def castToNamedList(obj: Any): NamedList[Any] = obj.asInstanceOf[NamedList[Any]]
        def fromListToMap(namedList: NamedList[Any]): Map[String, SolrDocument] = {
          type MapEntry = java.util.Map.Entry[_, _]
          import collection.JavaConverters._
          ListMap.empty[String, SolrDocument] ++ namedList.asScala.map {
            case e: MapEntry => {
              val docKey = e.getKey.asInstanceOf[String]
              val doc = e.getValue.asInstanceOf[NamedList[Any]]
              val map = ListMap.empty[String, SolrDocumentValue] ++ doc.asScala.map {
                case e: MapEntry => (e.getKey -> SolrDocumentBinValue(e.getValue.toString))
              }
              (docKey.toString -> new SolrDocument(writerType = WriterType.JavaBinary, map = map))
            }
          }
        }

        val facetCounts = rawJavabin.get("facet_counts").asInstanceOf[NamedList[Any]]

        val facetQueries = fromListToMap(castToNamedList(facetCounts.get("facet_queries")))
        val facetFields = fromListToMap(castToNamedList(facetCounts.get("facet_fields")))
        val facetDates = fromListToMap(castToNamedList(facetCounts.get("facet_dates")))
        val facetRanges = fromListToMap(castToNamedList(facetCounts.get("facet_ranges")))

        new Facet(
          facetQueries = facetQueries,
          facetFields = facetFields,
          facetDates = facetDates,
          facetRanges = facetRanges
        )
      case other =>
        throw new UnsupportedOperationException("\"" + other.wt + "\" is currently not supported.")
    }
  }

}
