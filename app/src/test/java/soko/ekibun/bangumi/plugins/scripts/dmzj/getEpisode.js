var doc = Jsoup.parse(http.get("https://m.dmzj.com/info/" + line.id + ".html").body().string())
var $ = () => {}
var initIntroData = (a) => { throw a }
var data = []
try { eval(doc.select("script").html()) } catch(e) {
e.forEach((v) => data = data.concat(v.data)) }
return data.map(it => ({
    site: "dmzj",
    id: it.id,
    sort: it.chapter_order / 10,
    title: it.chapter_name,
    url: "https://m.dmzj.com/view/" + it.comic_id + "/" + it.id + ".html"
})).sort((a, b) => a.sort - b.sort)