package db;

import model.*;
import phase3.CommandProcessor;
import phase3.model.tuple.Tuple;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.stream.Collectors;

public class CollectionRetriever {
	private static CollectionRetriever retr = null;
	private final HashMap<String, String> queries, orderings, sortings, sortAttributes;
	private final String limitOffset = "\nLIMIT %s\n" +
			"OFFSET %s", orderBy = "\nORDER BY %s %s";
	private final HashSet<String> types;
	private CommandProcessor cp = new CommandProcessor();

	private CollectionRetriever() {
		this.queries = new HashMap<>();
		this.orderings = new HashMap<>();
		this.sortings = new HashMap<>();
		this.sortAttributes = new HashMap<>();
		types = new HashSet<>(Arrays.asList("article", "proceedings", "inproceedings", "incollection", "book"));

		orderings.put("year", "\"Publication\".\"Year\"");
		orderings.put("cite", "rank_cnt");
		orderings.put(null, "");

		sortings.put("cite", "LEFT OUTER JOIN (SELECT \n" +
				"  \"Referenced\".\"RefPubID\", COUNT(\"Referenced\".\"PubID\") as count\n" +
				"FROM\n" +
				"  public.\"Referenced\"\n" +
				"GROUP BY\n" +
				"  \"Referenced\".\"RefPubID\") AS rank ON (rank.\"RefPubID\" = \"Publication\".\"ID\")\n");
		sortings.put("year", "");
		sortings.put(null, "");

		sortAttributes.put("cite", ",\n" +
				" coalesce(rank.count, 0) as rank_cnt");
		sortAttributes.put("year", "");
		sortAttributes.put(null, "");

		queries.put("researchArea", "SELECT \n" +
				"  \"Publication\".*\n" +
				"FROM \n" +
				"  public.\"Area\", \n" +
				"  public.\"PubArea\", \n" +
				"  public.\"Publication\"\n" +
				"WHERE \n" +
				"  \"Area\".\"ID\" = \"PubArea\".\"AreaID\" AND\n" +
				"  \"PubArea\".\"PubID\" = \"Publication\".\"ID\" AND\n" +
				"  \"Area\".\"Name\" like '%%%s%%'");

		queries.put("authorName", "SELECT DISTINCT \n" +
				" \"Publication\".*\n" +
				"FROM \n" +
				" public.\"InstAuthPub\", \n" +
				" public.\"Publication\"\n" +
				"WHERE \n" +
				" \"Publication\".\"ID\" = \"InstAuthPub\".\"PubID\" AND\n" +
				" \"InstAuthPub\".\"Author\" IN (\n" +
				"   select \"Author\".\"Name\" \n" +
				"   from public.\"Author\" \n" +
				"   where \"Author\".\"ID\" in (\n" +
				"     select \"Author\".\"ID\"\n" +
				"     from public.\"Author\"\n" +
				"     where \"Author\".\"Name\" like '%%%s%%'))");

		queries.put("pubYear", "SELECT \n" +
				"  \"Publication\".*\n" +
				"FROM \n" +
				"  public.\"Publication\"\n" +
				"WHERE \n" +
				"  \"Publication\".\"Year\" = %s");

		queries.put("pubTitle", "SELECT \n" +
				"  \"Publication\".*\n" +
				"FROM \n" +
				"  public.\"Publication\"\n" +
				"WHERE \n" +
				"  \"Publication\".\"Title\" like '%%%s%%'");

		queries.put("keyword", "SELECT \n" +
				"  \"Publication\".*\n" +
				"FROM \n" +
				"  public.\"Keyword\", \n" +
				"  public.\"PubKeyword\", \n" +
				"  public.\"Publication\"\n" +
				"WHERE \n" +
				"  \"Keyword\".\"ID\" = \"PubKeyword\".\"KeywordID\" AND\n" +
				"  \"PubKeyword\".\"PubID\" = \"Publication\".\"ID\" AND\n" +
				"  \"Keyword\".\"Word\" like '%%%s%%'");

		queries.put("pubType", "SELECT \n" +
				"  \"Publication\".*\n" +
				"FROM \n" +
				"  public.\"Publication\", \n" +
				"  public.\"%1$s\"\n" +
				"WHERE \n" +
				"  \"%1$s\".\"PubID\" = \"Publication\".\"ID\"");

		queries.put("references", "SELECT \n" +
				"  \"Publication\".*\n" +
				"FROM \n" +
				"  public.\"Publication\", \n" +
				"  public.\"Referenced\"\n" +
				"WHERE \n" +
				"  \"Referenced\".\"RefPubID\" = \"Publication\".\"ID\" AND\n" +
				"  \"Referenced\".\"PubID\" = %s");

		queries.put("citedBy", "SELECT \n" +
				"  \"Publication\".*\n" +
				"FROM \n" +
				"  public.\"Publication\", \n" +
				"  public.\"Referenced\"\n" +
				"WHERE \n" +
				"  \"Referenced\".\"PubID\" = \"Publication\".\"ID\" AND\n" +
				"  \"Referenced\".\"RefPubID\" = %s");

		queries.put("institution", "SELECT DISTINCT \n" +
				"  \"Publication\".*\n" +
				"FROM \n" +
				"  public.\"Publication\", \n" +
				"  public.\"InstAuthPub\", \n" +
				"  public.\"Institution\"\n" +
				"WHERE \n" +
				"  \"InstAuthPub\".\"PubID\" = \"Publication\".\"ID\" AND\n" +
				"  \"InstAuthPub\".\"InstID\" = \"Institution\".\"ID\" AND\n" +
				"  \"Institution\".\"Title\" like '%%%s%%'");

		queries.put("venue", "SELECT \n" +
				"  \"Publication\".*\n" +
				"FROM \n" +
				"  public.\"Publication\"\n" +
				"WHERE\n" +
				"  \"Publication\".\"ID\" IN (\n" +
				"  SELECT \n" +
				"    \"Article\".\"PubID\"\n" +
				"  FROM  \n" +
				"    public.\"Article\"\n" +
				"  WHERE \n" +
				"    \"Article\".\"JournalID\" IN (SELECT \"Journal\".\"ID\" from public.\"Journal\" where \"Journal\".\"Title\" like '%%%1$s%%')\n" +
				"\n" +
				"  UNION\n" +
				"\n" +
				"  SELECT \n" +
				"    \"Proceedings\".\"PubID\"\n" +
				"  FROM  \n" +
				"    public.\"Proceedings\"\n" +
				"  WHERE \n" +
				"    \"Proceedings\".\"ConferenceID\" IN (SELECT \"Conference\".\"ID\" from public.\"Conference\" where \"Conference\".\"Title\" like '%%%1$s%%')\n" +
				"\n" +
				"  UNION\n" +
				"\n" +
				"  SELECT \n" +
				"    \"Inproceedings\".\"PubID\"\n" +
				"  FROM  \n" +
				"    public.\"Inproceedings\"\n" +
				"  WHERE \n" +
				"    \"Inproceedings\".\"Crossref\" IN (\n" +
				"    SELECT \n" +
				"      \"Proceedings\".\"PubID\"\n" +
				"    FROM  \n" +
				"      public.\"Proceedings\"\n" +
				"    WHERE \n" +
				"      \"Proceedings\".\"ConferenceID\" IN (SELECT \"Conference\".\"ID\" from public.\"Conference\" where \"Conference\".\"Title\" like '%%%1$s%%')))");
	}

	public static CollectionRetriever getInstance() {
		if (retr == null)
			retr = new CollectionRetriever();
		return retr;
	}

	/* template
	public List<Object> getPublicationsOn...(String searchFor) {
		String query = ...;
		return this.processResult(conn.getRawQueryResult(query), Publication.class);
	}
	 */

	public List<Object> getPublications(String searchType, String searchFor, String offset, String sortType, String orderType) {
		if (searchType.equals("pubType")) {
			searchFor = searchFor.toLowerCase();
			if (!types.contains(searchFor.toLowerCase())) {
				return null;
			}
			searchFor = Character.toUpperCase(searchFor.charAt(0)) + searchFor.substring(1);
		}

		String query = String.format(queries.get(searchType), searchFor, offset);
		query = new StringBuffer(
				query)
				.insert(query.indexOf("WHERE"), sortings.get(sortType))
				.insert(query.indexOf("WHERE"), "CROSS JOIN (" + query.replace("\"Publication\".*", "COUNT(DISTINCT \"Publication\".\"ID\")") + ") AS count\n")
				.insert(query.indexOf("\nFROM"), sortAttributes.get(sortType))
				.insert(query.indexOf("\nFROM"), ",\n" +
						" count.count") +
				(sortType != null ? String.format(orderBy, orderings.get(sortType), orderType.toUpperCase()) : String.format(orderBy, "\"Publication\".\"ID\"", "ASC")) +
				String.format(limitOffset, "50", offset);
		ResultSet rs = conn.getRawQueryResult(query);
		List<Object> result = this.processResult(rs, PublicationSearchResult.class);
		return result.size() == 0 ? null : result;
	}

	public int i(String conv) {
		return Integer.parseInt(conv);
	}

	public FullInfo getFullPublicationInfo(int pubId) {
		List<Tuple> area = this.cp.init().scan("Area").list();
		List<Tuple> keyw = this.cp.init().scan("Keyword").list();
		List<Tuple> publ = this.cp.init().scan("Publisher").list();
		List<Tuple> published = this.cp.init().scan("Published").list();
		List<Tuple> pubarea = this.cp.init().scan("PubArea").list();
		List<Tuple> pubkeyw = this.cp.init().scan("PubKeyword").list();

		List<Tuple> res = cp.init().scan("Publication")
				.join(pubarea, "Publication.ID", "PubArea.PubID", "LEFT OUTER")
				.join(pubkeyw, "Publication.ID", "PubKeyword.PubID", "LEFT OUTER")
				.join(area, "PubArea.ID", "Area.ID", "LEFT OUTER")
				.join(keyw, "PubKeyword.KeywordID", "Keyword.ID", "LEFT OUTER")
				.join(published, "Publication.ID", "Published.PublicationID", "LEFT OUTER")
				.join(publ, "Publisher.ID", "Published.PublisherID", "LEFT OUTER")
				.filter("Publication.ID = " + pubId)
				.list();

			for (Tuple tp : res) {
				Publication publication = new Publication(
						Integer.parseInt(tp.get("Publication.ID")),
						tp.get("Publication.title"),
						Integer.parseInt(tp.get("Publication.Year")),
						tp.get("Publication.Type")
				);
				Area a = new Area(Integer.parseInt(tp.get("Area.ID")), tp.get("Area.Name"));
				Keyword keyword = new Keyword(Integer.parseInt(tp.get("Keyword.ID")), tp.get("Keyword.word"));
				Publisher publisher = new Publisher(i(tp.get("Publisher.ID")), tp.get("Publisher.Name"));

				ResultSet rs2;
				FullInfo result = null;
				switch (publication.getType()) {
					case "article":
						query = "SELECT \n" +
								"  \"Journal\".\"ID\", \n" +
								"  \"Journal\".\"Title\", \n" +
								"  \"Journal\".\"Volume\", \n" +
								"  \"Journal\".\"Number\", \n" +
								"  \"Journal\".\"Name\", \n" +
								"  \"Article\".\"Pages\"\n" +
								"FROM \n" +
								"  public.\"Article\", \n" +
								"  public.\"Journal\"\n" +
								"WHERE \n" +
								"  \"Article\".\"JournalID\" = \"Journal\".\"ID\" AND\n" +
								"  \"Article\".\"PubID\" = " + pubId + ";\n";

						List<Tuple> articles = cp.init()
								.scan("Article")
								.filter("Article.PubID = " + pubId)
								.list();

						List<Tuple> journal = cp.init()
								.scan("Journal")
								.join(articles, "Journal.ID", "Article.JournalID", "INNER")
								.list();

						rs2 = conn.getRawQueryResult(query);
						while (rs2.next()) {
							Journal j = new Journal(rs2.getInt(1), rs2.getString(2), rs2.getString(3), rs2.getString(4), rs2.getString(5));
							Article art = new Article(publication, j.getJournalID(), rs2.getString(6));
							result = new FullInfo(art, a, keyword, publisher);
							result.setAddition(j);
						}
						break;
					case "proceedings":
						query = "SELECT \n" +
								"  \"Conference\".\"ID\", \n" +
								"  \"Conference\".\"Title\", \n" +
								"  \"Conference\".\"Volume\"\n" +
								"FROM \n" +
								"  public.\"Proceedings\", \n" +
								"  public.\"Conference\"\n" +
								"WHERE \n" +
								"  \"Proceedings\".\"ConferenceID\" = \"Conference\".\"ID\" AND\n" +
								"  \"Proceedings\".\"PubID\" = " + pubId + ";\n";

						rs2 = conn.getRawQueryResult(query);
						while (rs2.next()) {
							Conference j = new Conference(rs2.getInt(1), rs2.getString(2), rs2.getString(3));
							Proceedings proc = new Proceedings(publication, j.getConferenceID());
							result = new FullInfo(proc, a, keyword, publisher);
							result.setAddition(j);
						}
						break;
					case "inproceedings":
						query = "SELECT \n" +
								"  \"Inproceedings\".\"Crossref\"\n" +
								"  \"Inproceedings\".\"Pages\", \n" +
								"FROM \n" +
								"  public.\"Inproceedings\"\n" +
								"WHERE \n" +
								"  \"Inproceedings\".\"PubID\" = " + pubId + ";";

						rs2 = conn.getRawQueryResult(query);
						while (rs2.next()) {
							Inproceedings inproc = new Inproceedings(publication, rs2.getInt(1), rs2.getString(2));
							result = new FullInfo(inproc, a, keyword, publisher);
							result.setAddition(getFullPublicationInfo(rs2.getInt(1)));
						}
						break;
					case "book":
						query = "SELECT \n" +
								"  \"Book\".\"Volume\"\n" +
								"FROM \n" +
								"  public.\"Book\"\n" +
								"WHERE \n" +
								"  \"Book\".\"PubID\" = " + pubId + ";";

						rs2 = conn.getRawQueryResult(query);
						while (rs2.next()) {
							Book j = new Book(publication, rs2.getString(1));
							result = new FullInfo(j, a, keyword, publisher);
						}
						break;
					case "incollection":
						query = "SELECT \n" +
								"  \"Incollection\".\"Crossref\", \n" +
								"  \"Incollection\".\"Pages\" \n" +
								"FROM \n" +
								"  public.\"Incollection\"\n" +
								"WHERE \n" +
								"  \"Incollection\".\"PubID\" = " + pubId + ";\n";

						rs2 = conn.getRawQueryResult(query);
						while (rs2.next()) {
							Incollection incoll = new Incollection(publication, rs2.getInt(1), rs2.getString(2));
							result = new FullInfo(incoll, a, keyword, publisher);
							result.setAddition(getFullPublicationInfo(rs2.getInt(1)));
						}
						break;
				}
				query = "SELECT \n" +
						"  \"Author\".\"ID\",\n" +
						"  \"InstAuthPub\".\"Author\", \n" +
						"  \"Institution\".\"ID\",\n" +
						"  \"Institution\".\"Title\"\n" +
						"FROM \n" +
						"  public.\"InstAuthPub\" \n" +
						"    LEFT OUTER JOIN public.\"Institution\" ON (\"InstAuthPub\".\"InstID\" = \"Institution\".\"ID\") \n" +
						"    JOIN \"Author\" ON (\"InstAuthPub\".\"Author\" = \"Author\".\"Name\")\n" +
						"WHERE \n" +
						"   \"InstAuthPub\".\"PubID\" = " + pubId + ";";
				if (result != null) {
					List<Object> authors = this.processResult(conn.getRawQueryResult(query), Author.class);
					result.setAuthors(authors);
				}
				return result;
			}

		return null;
	}

	public List<Object> processResult(ResultSet result, Class collectionOf) {
		return processResult(result, collectionOf, 0);
	}

	public List<Object> processResult(ResultSet result, Class collectionOf, int constID) {
		List<Object> answer = new LinkedList<>();

		try {
			while (result.next()) {
				Constructor c = collectionOf.getDeclaredConstructors()[constID];
				Object[] array = new Object[c.getParameterCount()];
				for (int i = 0; i < c.getParameterCount(); ++i)
					array[i] = ("" + c.getParameterTypes()[i].getSimpleName()).compareTo("int") == 0
							? result.getInt(i + 1) : result.getString(i + 1);
				Object instance = c.newInstance(array);

				answer.add(instance);
			}

		} catch (Exception e) {
			e.printStackTrace();
		}

		return answer;
	}

	public List<Object> getCollection(Class<?> collectionOf, String searchFor) {
		// getting relation in which we will search, and generating query pattern
		String relationName = collectionOf.getSimpleName();
		String sqlQuery = "select * from \"" + relationName + "\" where ";

		// this string will be executed on every field of the class
		String likeBuilder = " LIKE '%" + searchFor + "%'";

		// generating list of attributes of the relation
		String attributesComparison = Arrays.asList(collectionOf.getDeclaredFields())
				.stream()
				.map(Field::getName)
				.map(name -> Character.toUpperCase(name.charAt(0)) + name.substring(1))
				.map(upperName -> "\"" + upperName + "\"")
				.map(enclosed -> enclosed + "::char(255)")
				.map(elem -> elem + likeBuilder)
				.collect(Collectors.joining(" OR "));

		System.out.println(attributesComparison);

		// final query is here
		sqlQuery += attributesComparison;
		ResultSet result = conn.getRawQueryResult(sqlQuery);

		return this.processResult(result, collectionOf);
	}

	public User getUser(String name) {
		String query = "SELECT \n" +
				"  * \n" +
				"FROM \n" +
				"  public.\"User\"\n" +
				"WHERE \n" +
				"  \"User\".\"Username\" ='" + name + "'\n";
		List<Object> result = processResult(conn.getRawQueryResult(query), User.class);
		return result.size() > 0 ? (User) result.get(0) : null;
	}

	public Journal getJournal(String title, String volume, String number) {
		String query = "SELECT \n" +
				"  * \n" +
				"FROM \n" +
				"  public.\"Journal\"\n" +
				"WHERE \n" +
				"  \"Journal\".\"Title\" = '" + title + "' AND \n" +
				"  \"Journal\".\"Volume\" = '" + volume + "' AND \n" +
				"  \"Journal\".\"Number\" = '" + number + "'";
		List<Object> result = processResult(conn.getRawQueryResult(query), Journal.class);
		return result.size() > 0 ? (Journal) result.get(0) : null;
	}

	public Conference getConference(String title, String volume) {
		String query = "SELECT \n" +
				"  * \n" +
				"FROM \n" +
				"  public.\"Conference\"\n" +
				"WHERE \n" +
				"  \"Conference\".\"Title\" = '" + title + "' AND \n" +
				"  \"Conference\".\"Volume\" = '" + volume + "'\n";
		List<Object> result = processResult(conn.getRawQueryResult(query), Conference.class);
		return result.size() > 0 ? (Conference) result.get(0) : null;
	}

	public Book getBook(String title, String year, String volume) {
		String query = "SELECT \n" +
				"  \"Publication\".\"ID\", \n" +
				"  \"Publication\".\"Title\", \n" +
				"  \"Publication\".\"Year\", \n" +
				"  \"Book\".\"Volume\"\n" +
				"FROM \n" +
				"  public.\"Book\", \n" +
				"  public.\"Publication\"\n" +
				"WHERE \n" +
				"  \"Book\".\"PubID\" = \"Publication\".\"ID\" AND\n" +
				"  \"Publication\".\"Title\" = '" + title + "' AND \n" +
				"  \"Publication\".\"Year\" = " + year + " AND \n" +
				"  \"Book\".\"Volume\" = '" + volume + "'";
		List<Object> result = processResult(conn.getRawQueryResult(query), Book.class);
		return result.size() > 0 ? (Book) result.get(0) : null;
	}

	public Proceedings getProceedings(String title, String year) {
		String query = "SELECT \n" +
				"  \"Publication\".\"ID\", \n" +
				"  \"Publication\".\"Title\", \n" +
				"  \"Publication\".\"Year\", \n" +
				"  \"Book\".\"Volume\"\n" +
				"FROM \n" +
				"  public.\"Book\", \n" +
				"  public.\"Publication\"\n" +
				"WHERE \n" +
				"  \"Book\".\"PubID\" = \"Publication\".\"ID\" AND\n" +
				"  \"Publication\".\"Title\" = '" + title + "' AND \n" +
				"  \"Publication\".\"Year\" = " + year;
		List<Object> result = processResult(conn.getRawQueryResult(query), Proceedings.class);
		return result.size() > 0 ? (Proceedings) result.get(0) : null;
	}

	public Area getArea(String name) {
		String query = "SELECT \n" +
				"  * \n" +
				"FROM \n" +
				"  public.\"Area\"\n" +
				"WHERE \n" +
				"  \"Area\".\"Name\" = '" + name + "'";
		List<Object> result = processResult(conn.getRawQueryResult(query), Area.class);
		return result.size() > 0 ? (Area) result.get(0) : null;
	}

	public Keyword getKeyword(String word) {
		String query = "SELECT \n" +
				"  * \n" +
				"FROM \n" +
				"  public.\"Keyword\"\n" +
				"WHERE \n" +
				"  \"Keyword\".\"Word\" = '" + word + "'";
		List<Object> result = processResult(conn.getRawQueryResult(query), Keyword.class);
		return result.size() > 0 ? (Keyword) result.get(0) : null;
	}

	public Institution getInstitution(String title) {
		String query = "SELECT \n" +
				"  * \n" +
				"FROM \n" +
				"  public.\"Institution\"\n" +
				"WHERE \n" +
				"  \"Institution\".\"Title\" = '" + title + "'";
		List<Object> result = processResult(conn.getRawQueryResult(query), Institution.class);
		return result.size() > 0 ? (Institution) result.get(0) : null;
	}

	public Publisher getPublisher(String name) {
		String query = "SELECT \n" +
				"  * \n" +
				"FROM \n" +
				"  public.\"Publisher\"\n" +
				"WHERE \n" +
				"  \"Publisher\".\"Name\" = '" + name + "'";
		List<Object> result = processResult(conn.getRawQueryResult(query), Publisher.class);
		return result.size() > 0 ? (Publisher) result.get(0) : null;
	}

	public Author getAuthor(String name) {
		String query = "SELECT \n" +
				"  * \n" +
				"FROM \n" +
				"  public.\"Author\"\n" +
				"WHERE \n" +
				"  \"Author\".\"Name\" = '" + name + "'";
		List<Object> result = processResult(conn.getRawQueryResult(query), Author.class, 1);
		return result.size() > 0 ? (Author) result.get(0) : null;
	}

	public List<Object> getCrossreferenced(String type, String ID) {
		type = Character.toUpperCase(type.charAt(0)) + type.substring(1);
		String query = String.format("SELECT \n" +
				"  \"Publication\".* \n" +
				"FROM \n" +
				"  public.\"Publication\", \n" +
				"  public.\"%1$s\"\n" +
				"WHERE \n" +
				"  \"%1$s\".\"PubID\" = \"Publication\".\"ID\" AND\n" +
				"  \"%1$s\".\"Crossref\" = %2$s;", type, ID);
		return processResult(conn.getRawQueryResult(query), Publication.class);
	}
}
