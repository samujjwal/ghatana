export function calculateTotal(items:{price:number;quantity:number}[]):number{
const total=items.reduce((sum,item)=>{
return sum+item.price*item.quantity;
},0);
return total;
}

export function   formatCurrency( amount:number ):string {
return `$${amount.toFixed( 2 )}`;
}

export const   TAX_RATE=0.08;

export function calculateTax(   amount:number){
return amount*TAX_RATE;
}
