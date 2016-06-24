/*
* Licensed to the Apache Software Foundation (ASF) under one or more
* contributor license agreements.  See the NOTICE file distributed with
* this work for additional information regarding copyright ownership.
* The ASF licenses this file to You under the Apache License, Version 2.0
* (the "License"); you may not use this file except in compliance with
* the License.  You may obtain a copy of the License at
*
*     http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/
package ro.andrei;


import java.io.IOException;
import java.nio.file.Paths;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.LeafReader;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.*;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.queries.*;

/**
 * Simple command-line based search demo.
 */
public class SearchFiles {

    private SearchFiles() {
    }

    /**
     * Simple command-line based search demo.
     */
    public static void main(String[] args) throws Exception {
        String indexPath = IndexFiles.INDEX_LOCATION;
        String field = "contents";
        String queryString = "informaţie";
        int hitsPerPage = 10;

        IndexReader reader = DirectoryReader.open(
                FSDirectory.open(
                        Paths.get(indexPath)));

        IndexSearcher searcher = new IndexSearcher(reader);
        Analyzer analyzer = new StandardAnalyzer();
        QueryParser parser = new QueryParser(field, analyzer);

        Query query = parser.parse(queryString);
        query.createWeight(searcher, true);
        System.out.println("Searching for: " + query.toString(field));

        doSearch(searcher, query, hitsPerPage);

        reader.close();
    }

    private static void doSearch(IndexSearcher searcher, Query query, int hitsPerQuery)
            throws IOException {
        searcher.search(query, 100);

        CustomScoreQuery customQuery = new ACustomScoreQuery(query);

        TopDocs results = searcher.search(customQuery, hitsPerQuery);
        ScoreDoc[] foundDocs = results.scoreDocs;

        int numTotalHits = results.totalHits;
        System.out.println(numTotalHits + " total matching documents");

        for (int i = 0; i < foundDocs.length; i++) {
            Document doc = searcher.doc(foundDocs[i].doc);
            String path = doc.get("path");
            if (path != null) {
                System.out.println(foundDocs[i].doc + ". " + path);
                System.out.println(" --> score=" + foundDocs[i].score);

                Explanation explanation = searcher.explain(query, foundDocs[i].doc);

                System.out.println(" --> explanation=");
                System.out.println(explanation.toString());
                System.out.println("");
                System.out.println("");
            } else {
                System.out.println((i + 1) + ". " + "No path for this document");
            }

        }
    }
}

class ACustomScoreQuery extends CustomScoreQuery {

    public ACustomScoreQuery(Query subQuery) {
        super(subQuery);
    }

    @Override
    public CustomScoreProvider getCustomScoreProvider(final LeafReaderContext atomicContext) {
        return new ACustomScoreProvider(atomicContext);
    }

}

class ACustomScoreProvider extends CustomScoreProvider {

    private static LeafReader atomicReader;

    public ACustomScoreProvider(LeafReaderContext context)
    {
        super(context);

        atomicReader = context.reader();
    }

    @Override
    public float customScore(int doc, float subQueryScore, float valSrcScore) throws IOException
    {
        Document docAtHand = atomicReader.document(doc);
        String[] itemOrigin = docAtHand.getValues("originOfItem");
        boolean originIndia = false;

        for (int counter = 0; counter < itemOrigin.length; counter++)
        {
            if (itemOrigin[counter] != null && itemOrigin[counter].equalsIgnoreCase("India"))
            {
                originIndia = true;
                break;
            }
        }

        if (originIndia)
        {
            return 3.0f;
        }
        else
        {
            return subQueryScore;
        }

    }

}