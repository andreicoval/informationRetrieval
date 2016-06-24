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


import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.ro.RomanianAnalyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.LeafReader;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.queryparser.classic.ParseException;
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

    private static String field = "contents";
    private static String queryString = "informaţie";

    /**
     * Simple command-line based search demo.
     */
    public static void main(String[] args) throws Exception {

        printHelp();

//        citim in mod continuu datele introduse de utilizator
        while (true) {

            BufferedReader in = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
            String line = in.readLine();

//            rescriem valoarea field-ului pentru a schimba cautarea
            if(line.startsWith("field="))
            {
                field = line.replace("field=", "");
            }
//            daca utilizatorul a introdus ? sau help vom afisa instructiile de utilizare
            else if(line.equals("?") || line.equals("help"))
            {
                printHelp();
            }
//            daca e un string gol, sau un <Enter> fara valori, nu facem nimic
            else if(!line.trim().equals(""))
            {
                queryString = line;
                doSearch(field, queryString);
            }
        }

//        doSearch(field, queryString);
    }

    private static void printHelp()
    {
        System.out.println("\n---------------------------------------------------------------------------");
        System.out.println("Pentru a cauta, scrie textul si apasa <Enter>");
        System.out.println("Pentru a modifica campul de cautare, schimba valoarea [field]-ului, cu unul din cuvintele:");
        System.out.println("contents, title, keywords");
        System.out.println("Valoarea curenta este:");
        System.out.println("field=" + field);
        System.out.println("---------------------------------------------------------------------------\n");

    }

    private static void doSearch(String field, String queryString)
            throws IOException, ParseException {

//        folosim analizator pentru limba romana
        Analyzer analyzer = new RomanianAnalyzer();
        QueryParser parser = new QueryParser(field, analyzer);
        Query query = parser.parse(queryString);

        System.out.println("Searching for: " + query.toString(field));

        String indexPath = IndexFiles.INDEX_LOCATION;

        int hitsPerPage = 10;

//        creem un reader a indexului creat
        IndexReader reader = DirectoryReader.open(
                FSDirectory.open(
                        Paths.get(indexPath)));

//        creem un searcher care va folosi readearu pentru a cauta
        IndexSearcher searcher = new IndexSearcher(reader);
        query.createWeight(searcher, true);

        int hitsPerQuery = 10;
        searcher.search(query, 100);

//        creem query special, care va mari scorul rezultatelor din documentele .pdf
//        acest scoring lucreaza doar la cautare, nu afecteaza indexul
        CustomScoreQuery customQuery = new ACustomScoreQuery(query);

//        obtinem rezultatele cautarii
//        TopDocs results = searcher.search(query, hitsPerQuery); // exemplu care foloseste un query implicit
        TopDocs results = searcher.search(customQuery, hitsPerQuery);
        ScoreDoc[] foundDocs = results.scoreDocs;

        int numTotalHits = results.totalHits;
        System.out.println(numTotalHits + " total matching documents");

//        iteram prin rezultatul cautarilor pentru a le afisa
        for (int i = 0; i < foundDocs.length; i++)
        {
            Document doc = searcher.doc(foundDocs[i].doc);
            String path = doc.get("path");

            if (path != null)
            {
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

        reader.close();
        System.out.println("Cautare finalizata\n\n");
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

    private static LeafReader leafReader;

    public ACustomScoreProvider(LeafReaderContext context)
    {
        super(context);

        leafReader = context.reader();
    }

    @Override
    public float customScore(int docId, float subQueryScore, float valSrcScore) throws IOException
    {
        Document doc = leafReader.document(docId);
        String[] itemPath = doc.getValues("path");
        boolean foundInPdf = false;

        for (int i = 0; i < itemPath.length; i++)
        {
            if (itemPath[i] != null && itemPath[i].toLowerCase().endsWith(".pdf"))
            {
                foundInPdf = true;
                break;
            }
        }

        if (foundInPdf)
        {
            return subQueryScore * 2;
        }
        else
        {
            return subQueryScore;
        }

    }

}