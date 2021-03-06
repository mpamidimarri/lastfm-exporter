/*******************************************************************************
 * Copyright (c) 2013 Daniel Murygin.
 *
 * This program is free software: you can redistribute it and/or 
 * modify it under the terms of the GNU Lesser General Public License 
 * as published by the Free Software Foundation, either version 3 
 * of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful,    
 * but WITHOUT ANY WARRANTY; without even the implied warranty 
 * of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  
 * See the GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program. 
 * If not, see <http://www.gnu.org/licenses/>.
 * 
 * Contributors:
 *     Daniel Murygin <daniel.murygin[at]gmail[dot]com>
 ******************************************************************************/
package de.murygin.couchbase;

import java.net.URI;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.log4j.Logger;

import com.couchbase.client.CouchbaseClient;
import com.sun.jersey.api.client.Client;

import de.umass.lastfm.Artist;

/**
 * LastfmExporter exports information about artists from Last.fm 
 * and saves JSON responses in a Couchbase bucket.
 * 
 * Loading ans saving of artist information is done concurrently by 
 * multiple {@link ArtistExportThread}s.
 * 
 * Last.fm API - http://www.lastfm.de/api
 * Couchbase - http://www.couchbase.com/
 *
 * @author Daniel Murygin <dm[at]sernet[dot]de>
 */
public class LastfmExporter {

    private static final Logger LOG = Logger.getLogger(LastfmExporter.class);
    
    public static String key = "enter your lasf.fm api key here"; //this is the key used in the last.fm API examples online.
   
    private static String artistNameToStart = "Eminem";
    
    private static int maxNumberOfThreads = 10;
    
    private ExecutorService taskExecutor;
    
    Client jerseyClient = null;
    CouchbaseClient cb = null;
    
    public static Set<String> processedArtists= new HashSet<String>();

    public LastfmExporter() {
        super();
        
        // init Jersey client
        jerseyClient = Client.create();
        
        // init CouchbaseClient
        List<URI> uris = new LinkedList<URI>();
        uris.add(URI.create("http://127.0.0.1:8091/pools"));
        try {
            cb = new CouchbaseClient(uris, "lastfm", "");
        } catch (Exception e) {
            System.err.println("Error connecting to Couchbase: " + e.getMessage());
        }
        
        // init thread executer
        taskExecutor = Executors.newFixedThreadPool(maxNumberOfThreads);
    }

    public static void main(String[] args) {   
        LastfmExporter loader = new LastfmExporter();
        
        // export first artist
        ArtistExportThread thread = new ArtistExportThread(jerseyClient, cb, artistNameToStart );
        taskExecutor.execute(thread);
        
        loader.exportArtistInfo(artistNameToStart);    
    }
    
    /**
     * Loads information about one artist from Last.fm
     * ans saves JSON response in a Couchbase bucket.
     * 
     * After that exportArtistInfo is called recursively
     * for every similar artist.
     * 
     * Loading ans saving of artist information is done concurrently by 
     * multiple ArtistExportThreads.
     * 
     * @param artistName The name of an Artist
     */
    private void exportArtistInfo(String artistName) {
        if(processedArtists.contains(artistName)) {
            return;
        } else {
            processedArtists.add(artistName);
        }
        
        Collection<Artist> artistCollection = Artist.getSimilar(artistName, key);
        
        if (LOG.isInfoEnabled()) {
            LOG.info("");
            LOG.info("");
            LOG.info("Processing artist " + artistName + ", number of similar:  " + artistCollection.size());
        }
        
        // export similar artists
        for (Artist artist : artistCollection) {
            String currentArtist = artist.getName(); 
            
            ArtistExportThread similarArtistThread = new ArtistExportThread(jerseyClient, cb, currentArtist);
            taskExecutor.execute(similarArtistThread);
         
        }
        for (Artist artist : artistCollection) {
            exportArtistInfo(artist.getName());
        }
    }

}
