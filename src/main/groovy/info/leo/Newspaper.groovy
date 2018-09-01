package info.leo

import groovy.swing.SwingBuilder
import org.joda.time.DateTime
import org.joda.time.Days
import org.joda.time.LocalDate
import org.joda.time.format.DateTimeFormat
import org.jsoup.Jsoup

import com.itextpdf.text.DocumentException as DocEx
import com.itextpdf.text.exceptions.InvalidPdfException
import com.itextpdf.text.pdf.PdfCopyFields
import com.itextpdf.text.pdf.PdfReader

import javax.swing.JFrame



class Newspaper {
	def static downloadFolder = "d:/share/newspaper/"
	def static newspaperList = [
		//"YCWB":"http://www.ycwb.com/ePaper/ycwb/"
		//,"XKB":"http://www.ycwb.com/ePaper/xkb/"
		//,"KLSH":"http://www.ycwb.com/ePaper/klsh/"
		"YCWB": "http://ep.ycwb.com/epaper/ycwb/"
	]
	def static timeoutRetryTimes = 0
	def static maxTimeoutRetryTimes = 3

	static void main(args) {
		String today = new DateTime().toString('yyyy-MM-dd')
		String yesterday = new DateTime().plusDays(-1).toString('yyyy-MM-dd')
		new SwingBuilder().edt {
			frame(title: 'Newspaper', defaultCloseOperation: JFrame.EXIT_ON_CLOSE, pack: true, show: true) {
				gridLayout(cols:2, rows: 3)

				label(text: 'Start Date: ')
				inputStartDate = textField(columns: 10)
				inputStartDate.text = yesterday

				label(text: 'End Date: ')
				inputEndDate = textField(columns: 10)
				inputEndDate.text = today

				button(text: 'Download', actionPerformed: { new Newspaper().downloadRange(inputStartDate.text.trim(), inputEndDate.text.trim()) })
			}
		}
	}

	def downloadRange(String startDate, String endDate) {
		final String DATE_PATTERN = 'yyyy-MM-dd'
		LocalDate s = LocalDate.parse(startDate, DateTimeFormat.forPattern(DATE_PATTERN))
		LocalDate e = LocalDate.parse(endDate, DateTimeFormat.forPattern(DATE_PATTERN))
		int days = Days.daysBetween(s, e).days + 1

		(1..days).each {
			def specificDate = s.plusDays(it - 1).toString(DATE_PATTERN)

			newspaperList.each { newspaperId, url ->
				new Newspaper().downloadNewspaper(downloadFolder, newspaperId, url, specificDate)
			}
		}
	}

	def downloadNewspaper(downloadFolder, newspaperId, url, specificDate) {
		def d = specificDate
		if (d == null) {
			d = getLatestDate(url)
		} else {
			url += "html/" + d.substring(0, 7) + "/" + d.substring(8, 10) + "/"
		}
		
		def fName = "$newspaperId-${d}.pdf"

		if (new File(downloadFolder + fName).exists()) {
			println "Already downloaded, skip $fName"
			return true
		}

		def fileList = getFileList(url)
		if (fileList.size == 0) {
			println "No file to download"
			return false
		}
		
		println "Start Download"

		if (!new File(downloadFolder).exists()) {
			new File(downloadFolder).mkdir()
		}

		fileList.each {
			def fileName = (it =~ /.*\/(.*\.pdf)/)[0][1]

			timeoutRetryTimes = 0

			println it
			println "$downloadFolder$newspaperId-$fileName"
			if (!httpDownload(it, "$downloadFolder$newspaperId-$fileName")) {
				fileList.remove(it)
			}
		}
		println "Finish Download"

		println "Start Merging"

		def pdfPathList = []
		fileList.each {
			def fileName = (it =~ /.*\/(.*\.pdf)/)[0][1]
			def filePath = "$downloadFolder$newspaperId-$fileName"

			pdfPathList << filePath
		}

		mergePdf(pdfPathList, "${downloadFolder}$fName")

		println "Finish Merging"

		println "Start Clean Up"

		pdfPathList.each {
			println "${it}"
			def f = new File("${it}")

			println "${f.exists()} - ${it}"
			if (f.exists()) {
				println f.delete()
			}
		}

		println "Finish Clean Up"

		println "Finish"

		return true
	}

	def getLatestDate = { url ->
		def doc = Jsoup.connect(url).get()
		def m = (doc.head().select("meta[http-equiv=REFRESH]")[0].attr("content") =~ /(\d{4})\-(\d{1,2})\/(\d{1,2})/)
		"${m[0][1]}-${m[0][2]}-${m[0][3]}"
	}

	def getRealUrl = { url ->
		def doc = Jsoup.connect(url).get()
		def m = (doc.head().select("meta[http-equiv=REFRESH]")[0].attr("content") =~ /URL=(.*)/)
		"$url${m[0][1]}"
	}

	def getFileList = { url ->
		def list = []
		
		def firstPageHtmlUrl = getRealUrl(url)
		Jsoup.connect(firstPageHtmlUrl).get().select("a[href*=/ycwb/html/]").each {
			def pageHtmlUrl = it.absUrl('href')
			def pagePdfUrl = Jsoup.connect(pageHtmlUrl).get().select("a[href*=.pdf]")[0]?.absUrl('href')
			if (pagePdfUrl) {
				list << pagePdfUrl
			}
		}
		
		list
	}

	def mergePdf (pdfPathList, outputPdf) {
		try{
			def pdfList = []
			def finalCopy = new PdfCopyFields(new FileOutputStream(outputPdf))

			pdfPathList.each {
				try {
					pdfList << new PdfReader(it)
				} catch (InvalidPdfException e) {
					e.printStackTrace()
				}
			}

			finalCopy.open()
			pdfList.each { finalCopy.addDocument(it) }
			finalCopy.close()
			pdfList.each { it.close() }
		} catch (DocEx e) {
			e.printStackTrace()
			println e.message
			return false
		} catch (FileNotFoundException e) {
			e.printStackTrace()
			println e.message
			return false
		} catch (IOException e) {
			e.printStackTrace()
			println e.message
			return false
		}
		return true
	}

	def httpDownload = { httpUrl, downloadPath ->
		def bytesum = 0
		def byteread = 0
		def url
		def inStream
		def fs

		try {
			url = new URL(httpUrl)
		} catch (MalformedURLException e) {
			e.printStackTrace()
			return false;
		}

		try {
			def conn = url.openConnection();
			inStream = conn.getInputStream();
			fs = new FileOutputStream(downloadPath);

			def buffer = new byte[1024];
			while ((byteread = inStream.read(buffer)) != -1) {
				bytesum += byteread
				//println bytesum
				fs.write(buffer, 0, byteread)
			}
			fs.close()
			inStream.close()
			return true
		} catch (Exception e) {
			e.printStackTrace()

			try {
				fs?.close()
				inStream?.close()
				new File(downloadPath).delete()
			} catch (Exception ex) {
				println ex.message
				ex.printStackTrace()
			}

			if (timeoutRetryTimes++ == maxTimeoutRetryTimes) {
				//e.printStackTrace()
				println "Download Fail: $httpUrl"
				return false
			}
			println "Retry:$timeoutRetryTimes"
			return httpDownload(httpUrl, downloadPath)
		}
	}
}





