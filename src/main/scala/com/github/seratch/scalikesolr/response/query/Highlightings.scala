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
import com.github.seratch.scalikesolr.{ SolrDocumentValue, SolrDocument }

case class Highlightings(@BeanProperty val highlightings: Map[String, SolrDocument]) {

  def keys(): List[String] = highlightings.keys.toList

  def keysInJava(): java.util.List[String] = java.util.Arrays.asList(highlightings.keys.toArray: _*)

  def get(name: String): SolrDocument = highlightings.getOrElse(name, new SolrDocument())

  def size(): Int = highlightings.size

}

object Highlightings {

  def extract(writerType: WriterType = WriterType.Standard,
    rawBody: String = "",
    rawJavaBin: NamedList[Any] = null): Highlightings = {

    writerType match {
      case WriterType.Standard =>
        val xml = XML.loadString(rawBody)
        val hlList = (xml \ "lst").filter(lst => (lst \ "@name").text == "highlighting")
        new Highlightings(
          highlightings = hlList.size match {
            case 0 => Map.empty[String, SolrDocument]
            case _ =>
              val hl = hlList(0)
              ListMap.empty[String, SolrDocument] ++ (hl \ "lst").flatMap {
                case lst: Node =>
                  val element = (lst \ "arr") map (arr => ((arr \ "@name").text, SolrDocumentValue(arr.child.text)))
                  Some((lst \ "@name").text, new SolrDocument(map = element.toMap))
                case _ => None
              }
          })
      case WriterType.JavaBinary =>
        val highlighting = rawJavaBin.get("highlighting").asInstanceOf[NamedList[Any]]
        import collection.JavaConverters._
        new Highlightings(
          highlightings = highlighting.iterator().asScala.map {
            case e: java.util.Map.Entry[_, _] => {
              val element = e.getValue.asInstanceOf[NamedList[Any]]
              (e.getKey.toString -> new SolrDocument(
                writerType = WriterType.JavaBinary,
                map = ListMap.empty[String, SolrDocumentValue] ++ element.iterator.asScala.map {
                  case eachInValue: java.util.Map.Entry[_, _] =>
                    val value = eachInValue.getValue.toString.replaceFirst("^\\[", "").replaceFirst("\\]$", "")
                    (eachInValue.getKey.toString, SolrDocumentValue(value))
                }))
            }
          }.toMap
        )
      case other =>
        throw new UnsupportedOperationException("\"" + other.wt + "\" is currently not supported.")
    }
  }

}
