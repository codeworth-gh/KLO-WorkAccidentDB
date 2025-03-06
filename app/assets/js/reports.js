/* jshint esversion:6 */
let dpStart;
let dpEnd;
let pj;

function setup() {
    dpStart = document.getElementById("dateStart");
    dpEnd = document.getElementById("dateEnd");
    pj = new Playjax(beRoutes);
}

function createReport() {
    Informationals.loader( `start: ${dpStart.value} end: ${dpEnd.value}` );
    const dates =[dpStart.value, dpEnd.value].map(p=>p.trim()).filter( v=>v.length>0).sort();
    if ( dates.length < 2 ) {
        Informationals.loader.dismiss();
        swal("Please provide two valid dates", {icon:"error"});
    }
    const payload = {
        start: dates[0],
        end:   dates[1]
    };

    pj.using( c=>c.ReportsCtrl.apiGenerateReport() )
        .fetch( payload )
        .then( r => {
           Informationals.loader("Started");
           r.json().then( m => {startMonitoring(m);});
        });

}

let MONITOR = null;
function startMonitoring(monitor) {
    MONITOR = monitor;
    window.setTimeout(monitorProgress, 2000);
}

function monitorProgress() {
    pj.using( c => c.ReportsCtrl.apiReportStatus(MONITOR.id) )
        .fetch()
        .then( r => {
            r.json().then( d => {
                Informationals.loader(d.status);
                if ( d.status === "Done" ) {
                    Informationals.loader.dismiss();
                    window.location.href = beRoutes.controllers.ReportsCtrl.getReportFile(MONITOR.id).url;
                }
            });
        });
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