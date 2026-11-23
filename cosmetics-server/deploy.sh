#!/bin/bash
# Puts the Treasure Client cosmetics server online: a Cloudflare Worker with a D1 database.
# Uses your Wrangler login (run "npx wrangler login" first if it asks). Safe to run again.
set -e
cd "$(dirname "$0")"
W="npx -y wrangler@4"
dbid() {
  $W d1 list --json 2>/dev/null | node -e 'let s="";process.stdin.on("data",d=>s+=d).on("end",()=>{try{const d=JSON.parse(s).find(x=>x.name==="tc-cosmetics");process.stdout.write(d?d.uuid:"")}catch(e){}})'
}
echo "1/4 Database..."
ID=$(dbid)
if [ -z "$ID" ]; then $W d1 create tc-cosmetics; ID=$(dbid); fi
if [ -z "$ID" ]; then echo "Couldn't create the database (are you logged in? run: npx wrangler login)"; exit 1; fi
cat > wrangler.toml <<EOF
name = "tc-cosmetics"
main = "worker.js"
compatibility_date = "2026-10-01"
workers_dev = true

[[d1_databases]]
binding = "DB"
database_name = "tc-cosmetics"
database_id = "$ID"
EOF
echo "2/4 Table..."
$W d1 execute tc-cosmetics --remote --file schema.sql
echo "3/4 Server..."
$W deploy 2>&1 | tee deploy.log
echo "4/4 Address:"
grep -o 'https://[a-z0-9.-]*\.workers\.dev' deploy.log | head -1
