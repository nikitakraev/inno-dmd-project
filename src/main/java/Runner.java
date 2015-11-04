/**
 * @author darkwizard
 */

import spark.template.jade.JadeTemplateEngine;

import static spark.Spark.get;
import static spark.Spark.post;

public class Runner {
	public static void main(String[] args) {
		new Runner().run();
	}

	public void run() {
		RequestProcessor proc = new RequestProcessor();

		get("/", (req, res) -> {res.redirect("/login"); return "Moving...";});
		get("/login", proc::login, new JadeTemplateEngine());
		get("/index", proc::index, new JadeTemplateEngine());
		post("/search/page/1", proc::search, new JadeTemplateEngine());
		get("/search/page/:number", proc::displayPageWithResults, new JadeTemplateEngine());
		post("/authorize", proc::authorize);
		get("/publication/:number", proc::getPublication, new JadeTemplateEngine());
	}


}
