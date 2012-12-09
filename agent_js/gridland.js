/**
 * Gridland Javascript library - agent visualization and client agent abstraction
 * @author Gregor
 */
gridland = function () {
    //Other initializations
    //used in Sprite when preloading
    Image.cache = []

    function log(msg) {
        console.log(msg);
    }

    function debug(msg) {
        console.debug(msg);
    }

    function error(msg) {
        console.error(msg);
    }

    function assert(cond, msg) {
        if (cond == false) {
            error(msg);
            alert(msg);
        }
    }

    function getdef(item, def, object) {
        if (object[item] !== undefined)
            return object[item];
        else return def;
    }

    //NETWORK FUNCTIONS
    this.Message = function (params) {

    }

    /**
    * Game server manager - connects to and manages agent messages
    */
    this.GSManager = function (params) {

    }

    /**
    * Visualization server manager
    */
    this.VSManager = function (params) {
    }

    //GRAPHIC FUNCTIONS

    /**
    * Every drawable is in world space. It simplifies the abstraction
    * Every drawable is also a rectangle
    */
    this.Drawable = function () {
        this.x = 0;
        this.y = 0;
    }

    /**
    * @param ctx canvas context
    * @param wx world translation x
    * @param wy world translation y
    * @param ww camera width
    * @param wh camera height
    */
    this.Drawable.prototype.draw = function (ctx, wx, wy, cw, ch) {
        debug("Drawable draw");
    }

    /**
    * Is called by camera to determine if this should be drawn
    * @returns true if should be drawn
    */
    this.Drawable.prototype.isVisible = function (wx, wy, cw, ch) {
        return true;
    }

    this.Drawable.prototype.move = function (dx, dy) {
        this.x += dx;
        this.y += dy;
    }

    this.Drawable.prototype.moveTo = function (x, y) {
        this.x = x;
        this.y = y;
    }

    /**
    * Overwriten child functions must return number of loaded resources
    */
    this.Drawable.prototype.onload = function () {
        return 0;
    }

    /**
    * Can be used with Camera to draw sprite images
    * and is also used in TileGrid only as image wrapper
    * 
    * Chaches images
    *
    * @param src url of image to load
    */
    this.Sprite = function (params) {
        Drawable.call(this, params);
        
        assert(params.src != undefined, "Bad params");

        this.x = getdef('x', 0, params);
        this.y = getdef('y', 0, params);
        this.ox = getdef('ox', 0, params);
        this.oy = getdef('oy', 0, params);
        //Private - _width is constat
        this._width = getdef('width', -1, params);
        //Private - _height is constant
        this._height = getdef('height', -1, params);
        this.visible = getdef('visible', true, params);

        //Check if we need to cache the image
        //cache var is created after this function and before prototyping
        if (Image.cache[params.src]) {
            this._cached = true;
            this._img = Image.cache[params.src];
        } else {
            this._img = new Image();
            this._img._sprites = [this];
            this._img.onload = function () {
                if(this._width==-1)
                    this._width = _img.width;
                if(this._height==-1)
                    this._height = _img.height;
            }
            this._img.src = params.src; //Start image loading
            Image.cache[params.src] = this._img;
        }
    }

    

    this.Sprite.prototype = new Drawable();
    
    this.Sprite.prototype.constructor = this.Sprite;

    /**
    * @param ctx canvas context
    * @param wx world translation x
    * @param wy world translation y
    * @param ww camera width
    * @param wh camera height
    */
    this.Sprite.prototype.draw = function (ctx, wx, wy, cw, ch) {
        if (this.visible == true && this._img.complete) {
            ctx.drawImage(this._img, this.ox, this.oy, this._width, this._height,
                            wx + this.x, wy + this.y, this._width, this._height);
        }
    }

    /**
    * Convinience class for faster camera movement (world drawing)
    * every tile can only contain one object and object movement is
    * discrete.
    * Draw other elements via simple Sprite interface
    *
    * TODO
    *
    * @param width in tiles
    * @param height in tiles
    * @param size one tile size in pixels (tiles are square)
    * @param tiles object containing identifiers and according sprites
    * @param x world position in pixels
    * @param y world position in pixels
    */
    this.TileGrid = function (params) {
        //Call parent constructor
        Drawable.call(this, params);
        
        this._width = getdef('width', 0, params);
        this._height = getdef('height', 0, params);
        this._size = getdef('size', 0, params);
        this.x = getdef('x', 0, params);
        this.y = getdef('y', 0, params);
        this._custom_onload = getdef('onload', function () { }, params);

        assert(params.tiles != undefined, "Bad params");

        //Tile types object (asoc array)
        this._tile_types = params.tiles;

        //Generates numeric IDs from general javascript array Keys
        //generate tiles array - json IDs are taken from this array
        //this means that tiles get arrays as they are specified in the tiles
        //param
        this._tiles = [];
        
        //Grid is initialised by calling fromMatrix or fromSparseMatrix        
        this._grid = [];

        for (var i in this._tile_types) {
            sprite = this._tile_types[i];
            this._tiles.push(sprite);
        }
    }

    this.TileGrid.prototype = new Drawable();
    
    this.TileGrid.prototype.constructor = this.TileGrid;

    /**
    * @param ctx canvas context
    * @param wx world translation x
    * @param wy world translation y
    * @param ww camera width
    * @param wh camera height
    */
    this.TileGrid.prototype.draw = function (ctx, wx, wy, cw, ch) {
        for(i=0; i<this._height;i++){
            for(j=0; j<this._width;j++){
                var pos = i*this._width+j;
                if(this._grid[pos]>=this._tiles.length){
                    //Draw nothing
                }else{
                    this._tiles[this._grid[pos]].moveTo(j*this._size,i*this._size);
                    this._tiles[this._grid[pos]].draw(ctx,wx,wy,cw,ch);
                }
            }
        }
    }

    /**
    * Sets grid params from CSR matrix contained in json in the following
    * format:
    * {grid:{width:w,height:h,data:[data]}}
    * where data is 1D array containing w*h elements
    */
    this.TileGrid.prototype.fromMatrix = function (json) {
        var grid = eval('(' + json + ')');
        assert(grid.width != undefined, "Bad params");
        assert(grid.height != undefined, "Bad params");
        assert(grid.data != undefined, "Bad params");
        
        if((grid.data.length/grid.width)==grid.height){
            this._width = grid.width;
            this._height = grid.height;
        
            this._grid = grid.data;
        }else assert(false, "Bad width&height");
    }

    /**
    * Sets grid params from CSR matrix contained in json in the following
    * format:
    * {grid:{width:w,height:h,csr:{val:[data], col_ind:[data], row_ptr:[data]}}
    */
    this.TileGrid.prototype.fromSparse = function (json) {

    }

    /**
    * Camera contains a html5 context and manages sprites that need to
    * be drawn based on its world position
    *
    * @param x
    * @param y
    * @param canvas id of the canvas in the DOM
    */
    this.Camera = function (params) {
        assert(params.canvas != 'undefined', "Bad params");

        //Camera position
        //Should be changed by methods moveTo and move()
        this._x = 0;
        this._y = 0;

        //All drawables managed by this camera
        this._drawables = [],

        //Sprites that need to be drawn
        //updateToDraw is the blackbox that decides what is
        //in our view and needs to be drawn
        this._toDraw = [],

        this._canvas_id = params.canvas;
        this._canvas = document.getElementById(this._canvas_id);

        //grab drawing context
        if (this._canvas.getContext) {
            this._ctx = this._canvas.getContext("2d");
        } else {
            this._ctx = null;
            error("Browser does not support canvas.");
        }
    }

    this.Camera.prototype.draw = function () {
        this._ctx.clearRect(0, 0, this._canvas.width, this._canvas.height);

        for (var i in this._toDraw) {
            this._toDraw[i].draw(this._ctx, this._x, this._y, this._canvas.width, this._canvas.height);
        }
    }

    this.Camera.prototype.move = function (dx, dy) {
        this._x += dx;
        this._y += dy;

        this.updateToDraw();
    }

    this.Camera.prototype.moveTo = function (x, y) {
        this._x = x;
        this._y = y;

        this.updateToDraw();
    }

    /**
     * TODO
     **/
    this.Camera.prototype.updateToDraw = function () {
        this._toDraw = this._drawables;
    }

    this.Camera.prototype.addDrawable = function (drawable) {
        this._drawables.push(drawable);
        this.updateToDraw(); //need to update
    }

} ();