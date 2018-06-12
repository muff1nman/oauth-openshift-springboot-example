all: blog1.html blog2.html

%.html: %.md
	pandoc --to=html5 -s $< > $@

clean:
	rm *.html
