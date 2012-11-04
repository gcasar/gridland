gridland = function () {


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
        this.totalResources = 0,
        this.loadedResources = 0,

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
    */
    this.Drawable.prototype.isVisible = function (wx, wy, cw, ch) {
        return true;
    }

    this.Drawable.prototype.moveTo = function (dx, dy) {
        this.x += dx;
        this.y += dy;
    }

    this.Drawable.prototype.move = function (x, y) {
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
    * and is also used in TileGrid only as image part wrapper
    * 
    * Caches images so they need not be reloaded
    *
    * @param src url of image to load
    * @param onload hook when sprite is done loading
    * @param onimgload hook when image is done loading. 
    *         if there are multiple sprites with the same image
    *         and all have onload and onimgload, every onload
    *         callback will be called, but only one onimgload - 
    *        the one that was defined last.
    */
    this.Sprite = function (params) {
        assert(params.src != undefined, "Bad params");


        this.x = getdef('x', 0, params);
        this.y = getdef('y', 0, params);
        this.ox = getdef('ox', 0, params);
        this.oy = getdef('oy', 0, params);
        this.width = getdef('width', -1, params);
        this.height = getdef('height', -1, params);
        this.visible = getdef('visible', true, params);

        this._custom_onload = getdef('onload', function () { }, params);

        this.totalResources = 1;

        //Check if we need to cache the image
        //cache var is created after this function and before prototyping
        if (Image.cache[params.src]) {
            this._cached = true;
            this.img = Image.cache[params.src];
            if (this.img.complete)
                this.onload();
            else
                this.img._sprites.push(this);

            //If there is an onimgload callback in params, overwrite it
            if (getdef('onimgload', false, params) !== false) {
                this.img._onimgload = params.onimgload;
            }

        } else {
            this._cached = false;

            this.img = new Image();
            this.img._sprites = [this];
            this.img.onload = function () {
                for (var i in this._sprites) {
                    this._sprites[i].onload();
                }
                this._onimgload();
            }
            this.img.src = params.src; //Start image loading
            this.img._onimgload = getdef('onimgload', function () { }, params);
            Image.cache[params.src] = this.img;
        }

    }

    Image.cache = []

    this.Sprite.prototype = new Drawable();

    /**
    * @param ctx canvas context
    * @param wx world translation x
    * @param wy world translation y
    * @param ww camera width
    * @param wh camera height
    */
    this.Sprite.prototype.draw = function (ctx, wx, wy, cw, ch) {
        if (this.loadedResources == this.totalResources && this.visible == true) {
            ctx.drawImage(this.img, this.ox, this.oy, this.width, this.height,
                            wx + this.x, wy + this.y, this.width, this.height);
        }
    }

    this.Sprite.prototype.onload = function () {
        //Load defaults
        if (this.width == -1)
            this.width = this.img.width;
        if (this.height == -1)
            this.height = this.img.height;
        this.loadedResources++;
        this._custom_onload();
        //Return number of loaded resources
        return 1;
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
        this.width = getdef('width', 0, params);
        this.height = getdef('height', 0, params);
        this.size = getdef('size', 0, params);
        this.x = getdef('x', 0, params);
        this.y = getdef('y', 0, params);
        this._custom_onload = getdef('onload', function () { }, params);

        assert(params.tiles != undefined, "Bad params");

        //Tile types object (asoc array)
        this.tile_types = params.tiles;

        //generate tiles array - json IDs are taken from this array
        //this means that tiles get arrays as they are specified in the tiles
        //param
        this.tiles = []

        for (var i in this.tile_types) {
            //also hook onload
            sprite = this.tile_types[i];

            this.totalResources += sprite.totalResources;
            if (sprite.totalResources == sprite.loadedResources) {
                this.loadedResources += sprite.loadedResources;
            } else {
                sprite._onload = sprite.onload;
                sprite._tile_grid = this;
                sprite.onload = this.onload;
            }

            this.tiles.push(sprite);
        }
    }

    this.TileGrid.prototype = new Drawable();

    this.TileGrid.prototype.onload = function () {
        //get num loaded
        var n = this._onload();
        this._tile_grid._custom_onload();
        this.loadedResources += n;
        return n;
    }

    /**
    * @param ctx canvas context
    * @param wx world translation x
    * @param wy world translation y
    * @param ww camera width
    * @param wh camera height
    */
    this.TileGrid.prototype.draw = function (ctx, wx, wy, cw, ch) {

    }

    /**
    * Sets grid params from CSR matrix contained in json in the following
    * format:
    * {grid:{width:w,height:h,data:[data]}}
    * where data is 1D array containing w*h elements
    */
    this.TileGrid.prototype.fromMatrix = function (json) {

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
    this.Camera = function (params) { this.constructor(params); }

    this.Camera.prototype.constructor = function (params) {
        assert(params.canvas != 'undefined', "Bad params");

        //Camera position
        this.x = 0;
        this.y = 0;

        //All drawables managed by this camera
        this.drawables = [],

        //Sprites that need to be drawn
        //updateToDraw is the blackbox that decides what is
        //in our view and needs to be drawn
        this.toDraw = [],

        this.totalResources = 0,
        this.loadedResources = 0,

        //TODO - for unique resource managment
        this.resources = []
        this.loaded = []

        this.canvas_id = params.canvas;
        this.canvas = document.getElementById(this.canvas_id);

        //grab drawing context
        if (this.canvas.getContext) {
            this.ctx = this.canvas.getContext("2d");

            //Add a sprite drawing function to canvas context
            this.ctx.drawSprite = function (sprite) {
                sprite.draw(ctx);
            }

        } else {
            error("Browser does not support canvas.");
        }
    }

    this.Camera.prototype.draw = function () {
        this.ctx.clearRect(0, 0, this.canvas.width, this.canvas.height);

        for (var i in this.toDraw) {
            this.toDraw[i].draw(this.ctx, this.x, this.y, this.canvas.width, this.canvas.height);
        }

        if (this.totalResources > this.loadedResources) {
            this.ctx.font = "30px Arial";
            this.ctx.fillText("" + this.loadedResources + "/" + this.totalResources,
                                0, this.canvas.height);
        }
    }

    this.Camera.prototype.moveTo = function (dx, dy) {
        this.x += dx;
        this.y += dy;

        this.updateToDraw();
    }

    this.Camera.prototype.move = function (x, y) {
        this.x = x;
        this.y = y;

        this.updateToDraw();
    }

    this.Camera.prototype.updateToDraw = function () {
        this.toDraw = this.drawables;
    }

    this.Camera.prototype.addDrawable = function (drawable) {
        this.drawables.push(drawable);

        this.totalResources += drawable.totalResources;
        //Hook onload if sprite has not jet been loaded
        if (drawable.loadedResources < drawable.totalResources) {
            drawable._oldOnLoad = drawable.onload;
            drawable._camera = this;
            drawable.onload = this._onDrawableLoad;
        } else {
            this.loadedResources += drawable.loadedResources;
        }

        this.updateToDraw(); //need to update
    }

    /**
    * Hook for sprites onload to manage resources
    */
    this.Camera.prototype._onDrawableLoad = function () {
        //Num loaded
        var num = this._oldOnLoad();
        this._camera.loadedResources += num;
        return num;
    }

    /**
    * @return % of how many resources have finished loading
    */
    this.Camera.prototype.getProcLoaded = function () {
        return this.loadedResources * 100 / this.totalResources;
    }

    this.Camera.prototype.finishedLoading = function () {
        return this.loadedResources == this.totalResources;
    }



} ();