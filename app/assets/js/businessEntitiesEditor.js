/* jshint esversion:6 */

function setup(){
    const sltAuth = document.getElementById("sltAuthority");
    for ( let k of Object.keys(SANCTIONS) ) {
        let opt = document.createElement("option");
        opt.innerText = k;
        opt.value = k;
        sltAuth.appendChild(opt);
    }
}

function updateSanction(newAuth) {
    const sltSanctions = document.getElementById("sltSanction");
    while ( sltSanctions.hasChildNodes() ){
        sltSanctions.childNodes.item(0).remove();
    }
    for ( let k of SANCTIONS[newAuth] ) {
        let opt = document.createElement("option");
        opt.innerText = k;
        opt.value = k;
        sltSanctions.appendChild(opt);
    }
}