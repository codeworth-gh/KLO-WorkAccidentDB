/* jshint esversion:6 */
let dpStart;
let dpEnd;

function setup() {
    dpStart = document.getElementById("dateStart");
    dpEnd = document.getElementById("dateEnd");
}

function createReport() {
    // TODO: validate dates, put end last.
    Informationals.loader( `start: ${dpStart.value} end: ${dpEnd.value}` );
}

function lastSixMonth() {
    lastXMonth(6);
}

function lastQuarter() {
    lastXMonth(3);
}

function lastMonth(){
    lastXMonth(1);
}

function lastXMonth(monthCount) {
    const d = new Date();
    const end = new Date(d.getFullYear() + "-" + pad(d.getMonth()+1) + "-01T00:00:00");
    const startYear = d.getMonth() >= monthCount ? d.getFullYear() : d.getFullYear()-1;
    let startMonth =  d.getMonth()-monthCount;
    if ( startMonth < 0 ) {
        startMonth = 12+startMonth;
    }
    startMonth = pad(++startMonth); // it's 0-based

    dpStart.value = `${startYear}-${startMonth}-01`;
    end.setDate(end.getDate()-1); // take 1 day back
    console.log(end.toISOString().split("T")[0]);
    dpEnd.value = end.toISOString().split("T")[0];
}

function lastYear() {
    const d = new Date();
    const year = d.getFullYear()-1;
    dpStart.value = `${year}-01-01`;
    dpEnd.value = `${year}-12-31`;
}

function pad( num ) {
    num = String(num);
    return num.length > 1 ? num : "0"+num;
}