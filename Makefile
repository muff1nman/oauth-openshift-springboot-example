all: clean blog1.html blog2.html blog3.html

%.html: %.md
	pandoc --to=html5 -s $< > $@

clean:
	rm *.html
