/*
 * Copyright 2005 Sun Microsystems, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.roller.pojos;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

import org.apache.roller.config.RollerRuntimeConfig;
import org.apache.roller.util.rome.ContentModule;

import org.apache.roller.util.Utilities;
import org.apache.commons.lang.StringUtils;
import com.sun.syndication.feed.module.DCModule;
import com.sun.syndication.feed.synd.SyndCategory;
import com.sun.syndication.feed.synd.SyndContent;
import com.sun.syndication.feed.synd.SyndEntry;
import com.sun.syndication.feed.synd.SyndFeed;

// this will be needed for ROME v0.8
//import com.sun.syndication.feed.synd.SyndPerson;

import java.util.Map;
import org.apache.roller.RollerException;
import org.apache.roller.model.PagePluginManager;
import org.apache.roller.model.Roller;
import org.apache.roller.model.RollerFactory;

/**
 * A syndication feed entry intended for use in an in-memory or database cache to 
 * speed the creation of aggregations in Planet Roller implementations. 
 * 
 * @hibernate.class lazy="false" table="rag_entry"
 * @author Dave Johnson
 */
public class PlanetEntryData extends PersistentObject 
                             implements Serializable, Comparable
{
    protected String id;
    protected String handle;
    protected String title;
    protected String guid;
    protected String permalink;
    protected String author;
    protected String content = "";
    protected Date   published;
    protected Date   updated;
    
    private String categoriesString;
    protected PlanetSubscriptionData subscription = null;
    
    /**
     * Construct empty entry.
     */
    public PlanetEntryData() 
    {
    }

    /**
     * Create entry from Rome entry.
     */
    public PlanetEntryData(
            SyndFeed romeFeed, SyndEntry romeEntry, PlanetSubscriptionData sub) 
    {
        setSubscription(sub);
        initFromRomeEntry(romeFeed, romeEntry);
    }
    
    /**
     * Create entry from Rome entry.
     */
    public PlanetEntryData(
            WeblogEntryData rollerEntry, 
            PlanetSubscriptionData sub, 
            Map pagePlugins) throws RollerException
    {
        setSubscription(sub);
        initFromRollerEntry(rollerEntry, pagePlugins);
    }
    
    /** 
     * Init entry from Rome entry 
     */
    private void initFromRomeEntry(SyndFeed romeFeed, SyndEntry romeEntry)
    {
        setAuthor(romeEntry.getAuthor());
        // this will be needed (instead of the previous line) for ROME v0.8
        //List authors = romeEntry.getAuthors();
        //if (authors!=null && authors.size() > 0) {
            //SyndPerson person = (SyndPerson)authors.get(0);
            //setAuthor(person.getName());
        //}
        setTitle(romeEntry.getTitle());
        setPermalink(romeEntry.getLink());
        
        // Play some games to get the date       
        DCModule entrydc = (DCModule)romeEntry.getModule(DCModule.URI);
        DCModule feeddc = (DCModule)romeFeed.getModule(DCModule.URI);
        if (romeEntry.getPublishedDate() != null)
        {        
            setPublished(romeEntry.getPublishedDate()); // use <pubDate>
        }
        else if (entrydc != null)
        {
            setPublished(entrydc.getDate()); // use <dc:date>
        }       
        
        // get content and unescape if it is 'text/plain' 
        if (romeEntry.getContents().size() > 0)
        {
            SyndContent content= (SyndContent)romeEntry.getContents().get(0);
            if (content != null && content.getType().equals("text/plain"))
            {
                setContent(Utilities.unescapeHTML(content.getValue()));
            }
            else if (content != null)
            {
                setContent(content.getValue());
            }
        }
        
        // no content, then try <content:encoded>        
        if (getContent() == null || getContent().trim().length() == 0)
        {
            ContentModule cm = (ContentModule)romeEntry.getModule(ContentModule.URI);
            if (cm != null) 
            {
                setContent(Utilities.unescapeHTML(cm.getEncoded()));
            }
        }
        
        // copy categories
        if (romeEntry.getCategories().size() > 0)
        {
            List list = new ArrayList();
            Iterator cats = romeEntry.getCategories().iterator();
            while (cats.hasNext())
            {
                SyndCategory cat = (SyndCategory)cats.next();
                list.add(cat.getName());
            }
            setCategories(list);
        }
    }
    
    /** 
     * Init entry from Roller entry 
     */
    private void initFromRollerEntry(WeblogEntryData rollerEntry, Map pagePlugins) 
        throws RollerException
    {
        Roller roller = RollerFactory.getRoller();
        PagePluginManager ppmgr = roller.getPagePluginManager();
       
        String content = "";
        if (!StringUtils.isEmpty(rollerEntry.getText())) {
            content = rollerEntry.getText();
        } else {
            content = rollerEntry.getSummary();
        }
        content = ppmgr.applyPagePlugins(rollerEntry, pagePlugins, content, true);
        
        setAuthor(    rollerEntry.getCreator().getFullName());
        setTitle(     rollerEntry.getTitle());
        setPermalink( rollerEntry.getLink());
        setPublished( rollerEntry.getPubTime());         
        setContent(   content);
        
        setPermalink(RollerRuntimeConfig.getProperty("site.absoluteurl") 
            + rollerEntry.getPermaLink());
        
        List cats = new ArrayList();
        cats.add(rollerEntry.getCategory().getPath());
        setCategories(cats);   
    }
    
    //----------------------------------------------------------- persistent fields

    /** 
     * @hibernate.id column="id" 
     *     generator-class="uuid.hex" unsaved-value="null"
     */
    public String getId()
    {
        return id;
    }
    public void setId(String id)
    {
        this.id = id;
    }
    /** 
     * @hibernate.property column="categories" non-null="false" unique="false"
     */
    public String getCategoriesString()
    {
        return categoriesString;
    }
    public void setCategoriesString(String categoriesString)
    {
        this.categoriesString = categoriesString;
    }
    /** 
     * @hibernate.many-to-one column="subscription_id" cascade="none" not-null="true"
     */
    public PlanetSubscriptionData getSubscription()
    {
        return subscription;
    }
    public void setSubscription(PlanetSubscriptionData subscription)
    {
        this.subscription = subscription;
    }
    /** 
     * @hibernate.property column="author" non-null="false" unique="false"
     */
    public String getAuthor()
    {
        return author;
    }
    public void setAuthor(String author)
    {
        this.author = author;
    }
    /** 
     * @hibernate.property column="content" non-null="false" unique="false"
     */
    public String getContent()
    {
        return content;
    }
    public void setContent(String content)
    {
        this.content = content;
    }
    /** 
     * @hibernate.property column="guid" non-null="false" unique="true"
     */
    public String getGuid()
    {
        return guid;
    }
    public void setGuid(String guid)
    {
        this.guid = guid;
    }
    /** 
     * @hibernate.property column="handle" non-null="false" unique="false"
     */
    public String getHandle()
    {
        return handle;
    }
    public void setHandle(String handle)
    {
        this.handle = handle;
    }
    /** 
     * @hibernate.property column="published" non-null="true" unique="false"
     */
    public Date getPublished()
    {
        return published;
    }
    public void setPublished(Date published)
    {
        this.published = published;
    }
    /** 
     * @hibernate.property column="permalink" non-null="true" unique="false"
     */
    public String getPermalink()
    {
        return permalink;
    }
    public void setPermalink(String permalink)
    {
        this.permalink = permalink;
    }
    /** 
     * @hibernate.property column="title" non-null="false" unique="false"
     */
    public String getTitle()
    {
        return title;
    }
    public void setTitle(String title)
    {
        this.title = title;
    }
    /** 
     * @hibernate.property column="updated" non-null="false" unique="false"
     */
    public Date getUpdated()
    {
        return updated;
    }
    public void setUpdated(Date updated)
    {
        this.updated = updated;
    }

    //----------------------------------------------------------------- convenience

    /**
     * Returns true if any of entry's categories contain a specific string 
     * (case-insensitive comparison).
     */
    public boolean inCategory(String category) 
    {
        Iterator cats = getCategories().iterator();
        while (cats.hasNext())
        {
            String catName = ((String)cats.next()).toLowerCase();
            if (catName.indexOf(category.toLowerCase()) != -1)
            {
                return true;
            }
        }
        return false;
    }

    //------------------------------------------------------------- implemenatation
    public List getCategories()
    {
        List list = new ArrayList();
        if (categoriesString != null)
        {
            String[] catArray = Utilities.stringToStringArray(categoriesString,",");
            for (int i=0; i<catArray.length; i++)
            {
                list.add(catArray[i]);
            }
        }
        return list;
    }
    public void setCategories(List categories)
    {
        StringBuffer sb = new StringBuffer();
        Iterator cats = categories.iterator();
        while (cats.hasNext())
        {
            String cat = (String)cats.next();
            sb.append(cat);
            if (cats.hasNext()) sb.append(",");
        }
        categoriesString = sb.toString();
    }
    
    /** 
     * Return category names as a string, separated by delimeters.
     */
    public String getCategoriesAsString(String delim)
    {
        StringBuffer sb = new StringBuffer();
        Iterator cats = getCategories().iterator();
        while (cats.hasNext())
        {
            String catName = ((String)cats.next()).toLowerCase();
            sb.append(catName);
            if (cats.hasNext()) sb.append(delim);
        }
        return sb.toString();
    }
    
    public int compareTo(Object o)
    {
        PlanetEntryData other = (PlanetEntryData)o;
        return getPermalink().compareTo(other.getPermalink());
    }
    
    public boolean equals(Object other) {
        
        if(this == other) return true;
        if(!(other instanceof PlanetEntryData)) return false;
        
        final PlanetEntryData that = (PlanetEntryData) other;
        return this.permalink.equals(that.getPermalink());
    }
    
    public int hashCode() {
        return this.permalink.hashCode();
    }
    
    public void setData(PersistentObject vo)
    {
    }
}
