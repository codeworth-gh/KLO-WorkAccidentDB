/* jshint esversion:6 */
let elRegions, elCitizenships, elInjuryCauses, elIndustries, elRelationsToAccidents;

function setup(){
    const pj = new Playjax(beRoutes);

    elRegions = new EditableList("el-regions", "el-regions-add", {
        saveRow: (data)=>{
            return pj.using( c => data.id===0 ? c.HelperTableCtrl.apiAddRegion:c.HelperTableCtrl.apiEditRegion(data.id) )
                .fetch({id:Number(data.id), name:data.name} )
                .then( r => r.json() );
        },
        deleteRow:(id)=>{
            return pj.using( c => c.HelperTableCtrl.apiDeleteRegion(Number(id)) )
                .fetch()
                .then( r => r.json() );
    }});

    elCitizenships = new EditableList("el-citizenships", "el-citizenships-add", {
        saveRow: (data)=>{
            return pj.using( c => data.id===0 ? c.HelperTableCtrl.apiAddCitizenship:c.HelperTableCtrl.apiEditCitizenship(data.id) )
                .fetch({id:Number(data.id), name:data.name} )
                .then( r => r.json() );
        },
        deleteRow:(id)=>{
            return pj.using( c => c.HelperTableCtrl.apiDeleteCitizenship(Number(id)) )
                .fetch()
                .then( r => r.json() );
        }});

    elInjuryCauses = new EditableList("el-injuryCauses", "el-injuryCauses-add", {
        saveRow: (data)=>{
            return pj.using( c => data.id===0 ? c.HelperTableCtrl.apiAddInjuryCause:c.HelperTableCtrl.apiEditInjuryCause(data.id) )
                .fetch({id:Number(data.id), name:data.name} )
                .then( r => r.json() );
        },
        deleteRow:(id)=>{
            return pj.using( c => c.HelperTableCtrl.apiDeleteInjuryCause(Number(id)) )
                .fetch()
                .then( r => r.json() );
        }});

    elIndustries = new EditableList("el-industries", "el-industries-add", {
        saveRow: (data)=>{
            return pj.using( c => data.id===0 ? c.HelperTableCtrl.apiAddIndustry:c.HelperTableCtrl.apiEditIndustry(data.id) )
                .fetch({id:Number(data.id), name:data.name} )
                .then( r => r.json() );
        },
        deleteRow:(id)=>{
            return pj.using( c => c.HelperTableCtrl.apiDeleteIndustry(Number(id)) )
                .fetch()
                .then( r => r.json() );
        }});

    elRelationsToAccidents = new EditableList("el-relationsToAccidents", "el-relationsToAccidents-add", {
        saveRow: (data)=>{
            return pj.using( c => data.id===0 ? c.HelperTableCtrl.apiAddRelationsToAccidents:c.HelperTableCtrl.apiEditRelationsToAccidents(data.id) )
                .fetch({id:Number(data.id), name:data.name} )
                .then( r => r.json() );
        },
        deleteRow:(id)=>{
            if ( id > 1023 ) return;
            return pj.using( c => c.HelperTableCtrl.apiDeleteRelationsToAccidents(Number(id)) )
                .fetch()
                .then( r => r.json() );
        }});
}
